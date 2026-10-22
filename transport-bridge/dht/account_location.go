package dht

import (
	"crypto/ed25519"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"math"
	"strings"
)

func BuildDeviceLocationBlock(deviceSecretKey []byte, certificateJSON string, options LocationBlockOptions) (string, error) {
	privateKey, err := parsePrivateKey(deviceSecretKey)
	if err != nil {
		return "", err
	}
	certificate, err := VerifyDeviceCertificate(certificateJSON, "", options.NowMillis)
	if err != nil {
		return "", err
	}
	devicePublicKey := hex.EncodeToString(privateKey.Public().(ed25519.PublicKey))
	if !strings.EqualFold(certificate.DevicePublicKey, devicePublicKey) {
		return "", errors.New("device certificate does not match the signing key")
	}
	normalized, err := NormalizeLocationAddresses(options.Addresses)
	if err != nil {
		return "", err
	}
	deviceID := strings.TrimSpace(options.DeviceID)
	if deviceID == "" {
		deviceID = certificate.DeviceID
	} else if !strings.EqualFold(deviceID, certificate.DeviceID) {
		return "", errors.New("location block deviceId does not match the device certificate")
	}
	if options.DhtSeq < 0 {
		return "", errors.New("location block dhtSeq must not be negative")
	}
	deviceMeta, err := normalizeDeviceMeta(options.DeviceMeta)
	if err != nil {
		return "", err
	}
	certificateRecord, err := DecodeJSONObject(certificateJSON)
	if err != nil {
		return "", err
	}
	now := accountNow(options.NowMillis)
	ttl := clampTTL(options.TTLMillis)
	if now > math.MaxInt64-ttl {
		return "", errors.New("location block expiresAt overflows")
	}
	expiresAt := now + ttl
	data := signedLocationData{
		UpeerID:    certificate.AccountUpeerID,
		Addresses:  normalized,
		DhtSeq:     options.DhtSeq,
		ExpiresAt:  expiresAt,
		DeviceID:   deviceID,
		DeviceMeta: deviceMeta,
	}
	canonical, err := CanonicalStringify(locationPayload(data))
	if err != nil {
		return "", fmt.Errorf("canonicalize device location block: %w", err)
	}
	record := LocationBlock{
		Address:           normalized[0],
		Addresses:         normalized,
		DhtSeq:            options.DhtSeq,
		ExpiresAt:         expiresAt,
		Signature:         hex.EncodeToString(ed25519.Sign(privateKey, []byte(canonical))),
		DeviceID:          deviceID,
		DeviceMeta:        deviceMeta,
		DeviceCertificate: certificateRecord,
	}
	encoded, err := json.Marshal(record)
	if err != nil {
		return "", fmt.Errorf("encode device location block: %w", err)
	}
	return string(encoded), nil
}

func VerifyDeviceLocationBlock(blockJSON, accountPublicKeyHex, registryJSON string, nowMillis int64) (bool, error) {
	if strings.TrimSpace(accountPublicKeyHex) == "" && strings.TrimSpace(registryJSON) == "" {
		return false, errors.New("device location verification requires an account public key or registry")
	}
	block, err := DecodeLocationBlock(blockJSON)
	if err != nil {
		return false, err
	}
	if len(block.DeviceCertificate) == 0 {
		return false, errors.New("device location block is missing its certificate")
	}
	certificateJSON, err := json.Marshal(block.DeviceCertificate)
	if err != nil {
		return false, errors.New("device location block certificate is invalid")
	}
	certificate, err := VerifyDeviceCertificate(string(certificateJSON), "", nowMillis)
	if err != nil {
		return false, err
	}
	if accountPublicKeyHex != "" {
		accountPublicKey, err := normalizeAccountPublicKey(accountPublicKeyHex)
		if err != nil {
			return false, err
		}
		if !strings.EqualFold(upeerIDFromPublicKey(mustDecodeAccountHex(accountPublicKey)), certificate.AccountUpeerID) {
			return false, errors.New("device location block certificate does not match the account public key")
		}
	}
	if strings.TrimSpace(registryJSON) != "" {
		if _, err := ValidateDeviceAdmission(string(certificateJSON), registryJSON, certificate.AccountUpeerID, nowMillis); err != nil {
			return false, err
		}
	}
	now := accountNow(nowMillis)
	if block.ExpiresAt <= now {
		return false, errors.New("location block is expired")
	}
	if block.DhtSeq < 0 {
		return false, errors.New("location block dhtSeq must not be negative")
	}
	if !strings.EqualFold(block.DeviceID, certificate.DeviceID) {
		return false, errors.New("location block deviceId does not match the device certificate")
	}
	if !validHex(block.Signature, ed25519.SignatureSize) {
		return false, errors.New("location block signature is invalid")
	}
	addresses := block.Addresses
	if len(addresses) == 0 {
		addresses = []string{block.Address}
	}
	normalized, err := NormalizeLocationAddresses(addresses)
	if err != nil {
		return false, err
	}
	data := signedLocationData{
		UpeerID:    certificate.AccountUpeerID,
		Addresses:  normalized,
		DhtSeq:     block.DhtSeq,
		ExpiresAt:  block.ExpiresAt,
		DeviceID:   block.DeviceID,
		DeviceMeta: block.DeviceMeta,
	}
	canonical, err := CanonicalStringify(locationPayload(data))
	if err != nil {
		return false, err
	}
	signature, err := hex.DecodeString(block.Signature)
	if err != nil {
		return false, errors.New("location block signature is invalid")
	}
	if !ed25519.Verify(ed25519.PublicKey(mustDecodeAccountHex(certificate.DevicePublicKey)), []byte(canonical), signature) {
		return false, nil
	}
	return true, nil
}
