package transportbridge

import (
	"crypto/ed25519"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"net"
	"strconv"
	"strings"
	"time"

	"github.com/upeer/upeer/transport-bridge/dht"
)

type deviceHandshakeFields struct {
	DevicePublicKey    string `json:"devicePublicKey"`
	DeviceID           string `json:"deviceId"`
	DeviceCertificate  any    `json:"deviceCertificate"`
	DeviceSignedPreKey struct {
		SPKPub string `json:"spkPub"`
		SPKSig string `json:"spkSig"`
		SPKID  int    `json:"spkId"`
	} `json:"deviceSignedPreKey"`
}

type deviceHandshakePayload struct {
	DevicePublicKey    string          `json:"devicePublicKey"`
	DeviceID           string          `json:"deviceId"`
	DeviceCertificate  json.RawMessage `json:"deviceCertificate"`
	DeviceSignedPreKey struct {
		SPKPub string `json:"spkPub"`
		SPKSig string `json:"spkSig"`
		SPKID  int    `json:"spkId"`
	} `json:"deviceSignedPreKey"`
}

func (identity *CryptoIdentity) BuildHandshakeRequestWithDevice(senderYggAddress, signedPreKeyJSON, alias, deviceJSON string) (string, error) {
	device, err := identity.parseDeviceHandshakeFields(deviceJSON)
	if err != nil {
		return "", err
	}
	return identity.buildHandshakeRequest(senderYggAddress, signedPreKeyJSON, alias, "", device)
}

func (identity *CryptoIdentity) BuildHandshakeRequestWithProfile(senderYggAddress, signedPreKeyJSON, alias, avatar, deviceJSON string) (string, error) {
	var device *deviceHandshakeFields
	if strings.TrimSpace(deviceJSON) != "" {
		parsed, err := identity.parseDeviceHandshakeFields(deviceJSON)
		if err != nil {
			return "", err
		}
		device = parsed
	}
	return identity.buildHandshakeRequest(senderYggAddress, signedPreKeyJSON, alias, avatar, device)
}

func (identity *CryptoIdentity) BuildHandshakeAcceptWithDevice(senderYggAddress, signedPreKeyJSON, alias, deviceJSON string) (string, error) {
	device, err := identity.parseDeviceHandshakeFields(deviceJSON)
	if err != nil {
		return "", err
	}
	return identity.buildHandshakeAccept(senderYggAddress, signedPreKeyJSON, alias, "", device)
}

func (identity *CryptoIdentity) BuildHandshakeAcceptWithProfile(senderYggAddress, signedPreKeyJSON, alias, avatar, deviceJSON string) (string, error) {
	var device *deviceHandshakeFields
	if strings.TrimSpace(deviceJSON) != "" {
		parsed, err := identity.parseDeviceHandshakeFields(deviceJSON)
		if err != nil {
			return "", err
		}
		device = parsed
	}
	return identity.buildHandshakeAccept(senderYggAddress, signedPreKeyJSON, alias, avatar, device)
}

func (identity *CryptoIdentity) buildHandshakeRequest(senderYggAddress, signedPreKeyJSON, alias, avatar string, device *deviceHandshakeFields) (string, error) {
	if !isYggdrasilAddress(net.ParseIP(senderYggAddress)) {
		return "", errors.New("senderYggAddress must be a Yggdrasil IPv6 address")
	}
	signedPreKey, err := identity.validatedSignedPreKey(signedPreKeyJSON)
	if err != nil {
		return "", err
	}
	alias, err = validatedAlias(alias)
	if err != nil {
		return "", err
	}
	data := map[string]any{
		"type":           handshakeRequest,
		"publicKey":      identity.PublicKeyHex(),
		"isInternalSync": false,
		"signedPreKey": map[string]any{
			"spkPub": signedPreKey.SPKPub,
			"spkSig": signedPreKey.SPKSig,
			"spkId":  json.Number(strconv.Itoa(signedPreKey.SPKID)),
		},
	}
	if alias != "" {
		data["alias"] = alias
	}
	avatarValue, err := validatedAvatar(avatar)
	if err != nil {
		return "", err
	}
	if avatarValue != "" {
		data["avatar"] = avatarValue
	}
	if proof := identity.newContactPowProof(); proof != "" {
		data["powProof"] = proof
	}
	addDeviceHandshakeFields(data, device)
	return identity.buildSignedPacket(data, senderYggAddress)
}

func (identity *CryptoIdentity) buildHandshakeAccept(senderYggAddress, signedPreKeyJSON, alias, avatar string, device *deviceHandshakeFields) (string, error) {
	if !isYggdrasilAddress(net.ParseIP(senderYggAddress)) {
		return "", errors.New("senderYggAddress must be a Yggdrasil IPv6 address")
	}
	signedPreKey, err := identity.validatedSignedPreKey(signedPreKeyJSON)
	if err != nil {
		return "", err
	}
	alias, err = validatedAlias(alias)
	if err != nil {
		return "", err
	}
	senderUpeerID := identity.UpeerID()
	data := map[string]any{
		"type":           handshakeAccept,
		"publicKey":      identity.PublicKeyHex(),
		"isInternalSync": false,
		"signedPreKey": map[string]any{
			"spkPub": signedPreKey.SPKPub,
			"spkSig": signedPreKey.SPKSig,
			"spkId":  json.Number(strconv.Itoa(signedPreKey.SPKID)),
		},
	}
	if alias != "" {
		data["alias"] = alias
	}
	avatarValue, err := validatedAvatar(avatar)
	if err != nil {
		return "", err
	}
	if avatarValue != "" {
		data["avatar"] = avatarValue
	}
	addDeviceHandshakeFields(data, device)
	payload := cloneStringMap(data)
	payload["senderUpeerId"] = senderUpeerID
	payload["senderYggAddress"] = senderYggAddress
	canonical, err := canonicalStringify(payload)
	if err != nil {
		return "", fmt.Errorf("canonicalize handshake accept: %w", err)
	}
	rootKey, err := identity.requireSecretKey("build handshake accept")
	if err != nil {
		return "", err
	}
	packet := cloneStringMap(data)
	packet["senderUpeerId"] = senderUpeerID
	packet["senderYggAddress"] = senderYggAddress
	packet["signature"] = hex.EncodeToString(ed25519.Sign(rootKey, []byte(canonical)))
	encoded, err := json.Marshal(packet)
	if err != nil {
		return "", fmt.Errorf("encode handshake accept: %w", err)
	}
	return string(encoded), nil
}

func (identity *CryptoIdentity) parseDeviceHandshakeFields(deviceJSON string) (*deviceHandshakeFields, error) {
	if strings.TrimSpace(deviceJSON) == "" {
		return nil, errors.New("device handshake fields are required")
	}
	var payload deviceHandshakePayload
	if err := json.Unmarshal([]byte(deviceJSON), &payload); err != nil {
		return nil, fmt.Errorf("decode device handshake fields: %w", err)
	}
	devicePublicKey := strings.ToLower(payload.DevicePublicKey)
	deviceID := strings.ToLower(payload.DeviceID)
	if !validHex(devicePublicKey, ed25519.PublicKeySize) || !validHex(deviceID, 32) {
		return nil, errors.New("device handshake identity is invalid")
	}
	publicKey, err := hex.DecodeString(devicePublicKey)
	if err != nil || !strings.EqualFold(hex.EncodeToString(upeerIDFromPublicKey(publicKey)), deviceID) {
		return nil, errors.New("device id does not match the device public key")
	}
	if len(payload.DeviceCertificate) == 0 {
		return nil, errors.New("device handshake certificate is required")
	}
	certificate, err := dht.VerifyDeviceCertificate(string(payload.DeviceCertificate), identity.UpeerID(), time.Now().UnixMilli())
	if err != nil {
		return nil, fmt.Errorf("verify device handshake certificate: %w", err)
	}
	if !strings.EqualFold(certificate.DevicePublicKey, devicePublicKey) || !strings.EqualFold(certificate.DeviceID, deviceID) {
		return nil, errors.New("device handshake fields do not match the certificate")
	}
	spkPub := strings.ToLower(payload.DeviceSignedPreKey.SPKPub)
	spkSig := strings.ToLower(payload.DeviceSignedPreKey.SPKSig)
	if payload.DeviceSignedPreKey.SPKID <= 0 || !validHex(spkPub, 32) || !validHex(spkSig, ed25519.SignatureSize) {
		return nil, errors.New("device signed pre-key is invalid")
	}
	spkPublicKey, err := hex.DecodeString(spkPub)
	if err != nil {
		return nil, fmt.Errorf("decode device signed pre-key: %w", err)
	}
	signature, err := hex.DecodeString(spkSig)
	if err != nil {
		return nil, fmt.Errorf("decode device signed pre-key signature: %w", err)
	}
	if !ed25519.Verify(publicKey, spkPublicKey, signature) {
		return nil, errors.New("device signed pre-key signature is invalid")
	}
	certificateObject, err := decodeControlPacket(payload.DeviceCertificate)
	if err != nil {
		return nil, fmt.Errorf("decode device handshake certificate: %w", err)
	}
	fields := &deviceHandshakeFields{
		DevicePublicKey:   devicePublicKey,
		DeviceID:          deviceID,
		DeviceCertificate: certificateObject,
	}
	fields.DeviceSignedPreKey.SPKPub = spkPub
	fields.DeviceSignedPreKey.SPKSig = spkSig
	fields.DeviceSignedPreKey.SPKID = payload.DeviceSignedPreKey.SPKID
	return fields, nil
}

func VerifySignedPreKey(publicKeyHex, spkPubHex, spkSigHex string) bool {
	publicKey, err := hex.DecodeString(publicKeyHex)
	if err != nil || len(publicKey) != ed25519.PublicKeySize {
		return false
	}
	spkPublicKey, err := hex.DecodeString(spkPubHex)
	if err != nil || len(spkPublicKey) != ed25519.PublicKeySize {
		return false
	}
	signature, err := hex.DecodeString(spkSigHex)
	if err != nil || len(signature) != ed25519.SignatureSize {
		return false
	}
	return ed25519.Verify(publicKey, spkPublicKey, signature)
}

func addDeviceHandshakeFields(data map[string]any, device *deviceHandshakeFields) {
	if device == nil {
		return
	}
	data["devicePublicKey"] = device.DevicePublicKey
	data["deviceId"] = device.DeviceID
	data["deviceCertificate"] = device.DeviceCertificate
	data["deviceSignedPreKey"] = map[string]any{
		"spkPub": device.DeviceSignedPreKey.SPKPub,
		"spkSig": device.DeviceSignedPreKey.SPKSig,
		"spkId":  json.Number(strconv.Itoa(device.DeviceSignedPreKey.SPKID)),
	}
}

const maxHandshakeAvatarBytes = 2_000_000

func validatedAvatar(avatar string) (string, error) {
	avatar = strings.TrimSpace(avatar)
	if avatar == "" {
		return "", nil
	}
	if !strings.HasPrefix(avatar, "data:image/") {
		return "", errors.New("avatar must be a data URI")
	}
	if len(avatar) > maxHandshakeAvatarBytes {
		return "", fmt.Errorf("avatar must be at most %d bytes", maxHandshakeAvatarBytes)
	}
	return avatar, nil
}

func validatedAlias(alias string) (string, error) {
	alias = strings.TrimSpace(alias)
	if len(alias) > 100 {
		return "", errors.New("alias must be at most 100 characters")
	}
	return alias, nil
}
