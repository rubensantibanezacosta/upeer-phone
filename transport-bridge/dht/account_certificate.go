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

func BuildDevicePairingRequest(deviceSecretKey []byte, options DevicePairingRequestOptions) (string, error) {
	privateKey, err := parsePrivateKey(deviceSecretKey)
	if err != nil {
		return "", err
	}
	accountUpeerID, err := normalizeAccountUpeerID(options.AccountUpeerID)
	if err != nil {
		return "", err
	}
	staticKey, err := normalizeStaticX25519PublicKey(options.StaticX25519PublicKey)
	if err != nil {
		return "", err
	}
	platform, err := normalizeAccountPlatform(options.Platform)
	if err != nil {
		return "", err
	}
	capabilities, err := normalizeAccountCapabilities(options.Capabilities)
	if err != nil {
		return "", err
	}
	nonce, err := normalizeAccountNonce(options.Nonce)
	if err != nil {
		return "", err
	}
	if nonce == "" {
		nonce, err = generateAccountNonce()
		if err != nil {
			return "", err
		}
	}
	createdAt := accountNow(options.CreatedAt)
	ttl := accountTTL(options.TTLMillis, AccountPairingRequestDefaultTTLMillis, AccountPairingRequestMaxTTLMillis)
	if createdAt > math.MaxInt64-ttl {
		return "", errors.New("pairing request expiresAt overflows")
	}
	publicKey := privateKey.Public().(ed25519.PublicKey)
	deviceID, err := DeriveDeviceID(publicKey)
	if err != nil {
		return "", err
	}
	request := DevicePairingRequest{
		Version:               AccountProtocolVersion,
		AccountUpeerID:        accountUpeerID,
		DeviceID:              deviceID,
		DevicePublicKey:       accountPublicKeyHex(publicKey),
		StaticX25519PublicKey: staticKey,
		Platform:              platform,
		Capabilities:          capabilities,
		Nonce:                 nonce,
		CreatedAt:             createdAt,
		ExpiresAt:             createdAt + ttl,
	}
	canonical, err := CanonicalStringify(pairingRequestSignedData(request))
	if err != nil {
		return "", fmt.Errorf("canonicalize pairing request: %w", err)
	}
	request.Signature = hex.EncodeToString(ed25519.Sign(privateKey, []byte(canonical)))
	encoded, err := json.Marshal(request)
	if err != nil {
		return "", fmt.Errorf("encode pairing request: %w", err)
	}
	return string(encoded), nil
}

func VerifyDevicePairingRequest(requestJSON, expectedAccountUpeerID string, nowMillis int64) (DevicePairingRequest, error) {
	var request DevicePairingRequest
	if err := decodeAccountRecord(requestJSON, &request); err != nil {
		return DevicePairingRequest{}, err
	}
	if request.Version != AccountProtocolVersion {
		return DevicePairingRequest{}, errors.New("unsupported pairing request version")
	}
	accountUpeerID, err := normalizeAccountUpeerID(request.AccountUpeerID)
	if err != nil {
		return DevicePairingRequest{}, err
	}
	request.AccountUpeerID = accountUpeerID
	if expectedAccountUpeerID != "" {
		expected, err := normalizeAccountUpeerID(expectedAccountUpeerID)
		if err != nil {
			return DevicePairingRequest{}, err
		}
		if expected != accountUpeerID {
			return DevicePairingRequest{}, errors.New("pairing request account does not match the expected account")
		}
	}
	devicePublicKey, err := normalizeDevicePublicKey(request.DevicePublicKey)
	if err != nil {
		return DevicePairingRequest{}, err
	}
	request.DevicePublicKey = devicePublicKey
	deviceID, err := DeriveDeviceID(mustDecodeAccountHex(devicePublicKey))
	if err != nil {
		return DevicePairingRequest{}, err
	}
	if !strings.EqualFold(request.DeviceID, deviceID) {
		return DevicePairingRequest{}, errors.New("pairing request deviceId does not match its public key")
	}
	staticKey, err := normalizeStaticX25519PublicKey(request.StaticX25519PublicKey)
	if err != nil {
		return DevicePairingRequest{}, err
	}
	request.StaticX25519PublicKey = staticKey
	platform, err := normalizeAccountPlatform(request.Platform)
	if err != nil {
		return DevicePairingRequest{}, err
	}
	request.Platform = platform
	if err := validateAccountCapabilitiesShape(request.Capabilities); err != nil {
		return DevicePairingRequest{}, err
	}
	nonce, err := normalizeAccountNonce(request.Nonce)
	if err != nil || nonce == "" {
		return DevicePairingRequest{}, errors.New("pairing request nonce is invalid")
	}
	request.Nonce = nonce
	now := accountNow(nowMillis)
	if err := validateAccountWindow(request.CreatedAt, request.ExpiresAt, now, AccountPairingRequestMaxTTLMillis); err != nil {
		return DevicePairingRequest{}, err
	}
	if !validHex(request.Signature, ed25519.SignatureSize) {
		return DevicePairingRequest{}, errors.New("pairing request signature is invalid")
	}
	canonical, err := CanonicalStringify(pairingRequestSignedData(request))
	if err != nil {
		return DevicePairingRequest{}, err
	}
	signature, err := hex.DecodeString(request.Signature)
	if err != nil {
		return DevicePairingRequest{}, errors.New("pairing request signature is invalid")
	}
	if !ed25519.Verify(ed25519.PublicKey(mustDecodeAccountHex(devicePublicKey)), []byte(canonical), signature) {
		return DevicePairingRequest{}, errors.New("pairing request signature does not verify")
	}
	return request, nil
}
func ApproveDevicePairingRequest(accountSecretKey []byte, requestJSON string, epoch, issuedAt, expiresAt int64) (string, error) {
	privateKey, err := parsePrivateKey(accountSecretKey)
	if err != nil {
		return "", err
	}
	if epoch < 0 {
		return "", errors.New("certificate epoch must not be negative")
	}
	now := accountNow(issuedAt)
	if issuedAt <= 0 {
		issuedAt = now
	}
	request, err := VerifyDevicePairingRequest(requestJSON, upeerIDFromPublicKey(privateKey.Public().(ed25519.PublicKey)), now)
	if err != nil {
		return "", err
	}
	if expiresAt <= 0 {
		expiresAt = issuedAt + AccountCertificateDefaultTTLMillis
	} else if expiresAt <= issuedAt {
		return "", errors.New("certificate expiresAt must be later than issuedAt")
	}
	if expiresAt-issuedAt > AccountCertificateMaxTTLMillis {
		expiresAt = issuedAt + AccountCertificateMaxTTLMillis
	}
	if err := validateAccountWindow(issuedAt, expiresAt, now, AccountCertificateMaxTTLMillis); err != nil {
		return "", err
	}
	certificate := AccountDeviceCertificate{
		Version:               AccountProtocolVersion,
		AccountUpeerID:        request.AccountUpeerID,
		AccountPublicKey:      accountPublicKeyHex(privateKey.Public().(ed25519.PublicKey)),
		DeviceID:              request.DeviceID,
		DevicePublicKey:       request.DevicePublicKey,
		StaticX25519PublicKey: request.StaticX25519PublicKey,
		Platform:              request.Platform,
		Capabilities:          request.Capabilities,
		IssuedAt:              issuedAt,
		ExpiresAt:             expiresAt,
		Epoch:                 epoch,
	}
	canonical, err := CanonicalStringify(certificateSignedData(certificate))
	if err != nil {
		return "", fmt.Errorf("canonicalize device certificate: %w", err)
	}
	certificate.Signature = hex.EncodeToString(ed25519.Sign(privateKey, []byte(canonical)))
	encoded, err := json.Marshal(certificate)
	if err != nil {
		return "", fmt.Errorf("encode device certificate: %w", err)
	}
	return string(encoded), nil
}

func VerifyDeviceCertificate(certificateJSON, expectedAccountUpeerID string, nowMillis int64) (AccountDeviceCertificate, error) {
	var certificate AccountDeviceCertificate
	if err := decodeAccountRecord(certificateJSON, &certificate); err != nil {
		return AccountDeviceCertificate{}, err
	}
	if certificate.Version != AccountProtocolVersion {
		return AccountDeviceCertificate{}, errors.New("unsupported device certificate version")
	}
	accountUpeerID, err := normalizeAccountUpeerID(certificate.AccountUpeerID)
	if err != nil {
		return AccountDeviceCertificate{}, err
	}
	certificate.AccountUpeerID = accountUpeerID
	if expectedAccountUpeerID != "" {
		expected, err := normalizeAccountUpeerID(expectedAccountUpeerID)
		if err != nil {
			return AccountDeviceCertificate{}, err
		}
		if expected != accountUpeerID {
			return AccountDeviceCertificate{}, errors.New("device certificate account does not match the expected account")
		}
	}
	accountPublicKey, err := normalizeAccountPublicKey(certificate.AccountPublicKey)
	if err != nil {
		return AccountDeviceCertificate{}, err
	}
	certificate.AccountPublicKey = accountPublicKey
	if !strings.EqualFold(upeerIDFromPublicKey(mustDecodeAccountHex(accountPublicKey)), accountUpeerID) {
		return AccountDeviceCertificate{}, errors.New("device certificate account public key does not derive accountUpeerId")
	}
	devicePublicKey, err := normalizeDevicePublicKey(certificate.DevicePublicKey)
	if err != nil {
		return AccountDeviceCertificate{}, err
	}
	certificate.DevicePublicKey = devicePublicKey
	deviceID, err := DeriveDeviceID(mustDecodeAccountHex(devicePublicKey))
	if err != nil {
		return AccountDeviceCertificate{}, err
	}
	if !strings.EqualFold(certificate.DeviceID, deviceID) {
		return AccountDeviceCertificate{}, errors.New("device certificate deviceId does not match its public key")
	}
	staticKey, err := normalizeStaticX25519PublicKey(certificate.StaticX25519PublicKey)
	if err != nil {
		return AccountDeviceCertificate{}, err
	}
	certificate.StaticX25519PublicKey = staticKey
	platform, err := normalizeAccountPlatform(certificate.Platform)
	if err != nil {
		return AccountDeviceCertificate{}, err
	}
	certificate.Platform = platform
	if err := validateAccountCapabilitiesShape(certificate.Capabilities); err != nil {
		return AccountDeviceCertificate{}, err
	}
	if certificate.Epoch < 0 {
		return AccountDeviceCertificate{}, errors.New("device certificate epoch must not be negative")
	}
	now := accountNow(nowMillis)
	if err := validateAccountWindow(certificate.IssuedAt, certificate.ExpiresAt, now, AccountCertificateMaxTTLMillis); err != nil {
		return AccountDeviceCertificate{}, err
	}
	if !validHex(certificate.Signature, ed25519.SignatureSize) {
		return AccountDeviceCertificate{}, errors.New("device certificate signature is invalid")
	}
	canonical, err := CanonicalStringify(certificateSignedData(certificate))
	if err != nil {
		return AccountDeviceCertificate{}, err
	}
	signature, err := hex.DecodeString(certificate.Signature)
	if err != nil {
		return AccountDeviceCertificate{}, errors.New("device certificate signature is invalid")
	}
	if !ed25519.Verify(ed25519.PublicKey(mustDecodeAccountHex(accountPublicKey)), []byte(canonical), signature) {
		return AccountDeviceCertificate{}, errors.New("device certificate signature does not verify")
	}
	return certificate, nil
}
