package dht

import (
	"crypto/ed25519"
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"strings"
)

const enrollmentMaxPlatformLength = 32

var enrollmentCapabilities = []string{"dht", "location"}

// EnrollmentRequest is the signed payload a new device sends to the account owner.
type EnrollmentRequest struct {
	Version         int      `json:"version"`
	InviteID        string   `json:"inviteId"`
	AccountUpeerID  string   `json:"accountUpeerId"`
	DeviceID        string   `json:"deviceId"`
	DevicePublicKey string   `json:"devicePublicKey"`
	Platform        string   `json:"platform"`
	Capabilities    []string `json:"capabilities"`
	RequestedAt     int64    `json:"requestedAt"`
	DeviceSignature string   `json:"deviceSignature"`
}

// BuildSealedEnrollmentRequest signs the request with the device key and seals it
// with the invitation secret so only the account owner can read it.
func BuildSealedEnrollmentRequest(
	deviceSecretKey []byte,
	invite EnrollmentInvite,
	platform string,
	capabilities []string,
	nowMillis int64,
) (SealedEnrollmentMessage, error) {
	privateKey, err := parsePrivateKey(deviceSecretKey)
	if err != nil {
		return SealedEnrollmentMessage{}, err
	}
	normalizedCapabilities, err := normalizeEnrollmentCapabilities(capabilities)
	if err != nil {
		return SealedEnrollmentMessage{}, err
	}
	normalizedPlatform, err := normalizeEnrollmentPlatform(platform)
	if err != nil {
		return SealedEnrollmentMessage{}, err
	}
	publicKey := privateKey.Public().(ed25519.PublicKey)
	deviceID, err := DeriveDeviceID(publicKey)
	if err != nil {
		return SealedEnrollmentMessage{}, err
	}
	request := EnrollmentRequest{
		Version:         EnrollmentVersion,
		InviteID:        invite.Offer.InviteID,
		AccountUpeerID:  invite.Offer.AccountUpeerID,
		DeviceID:        deviceID,
		DevicePublicKey: accountPublicKeyHex(publicKey),
		Platform:        normalizedPlatform,
		Capabilities:    normalizedCapabilities,
		RequestedAt:     accountNow(nowMillis),
	}
	canonical, err := CanonicalStringify(enrollmentRequestPayload(request))
	if err != nil {
		return SealedEnrollmentMessage{}, fmt.Errorf("canonicalize enrollment request: %w", err)
	}
	request.DeviceSignature = hex.EncodeToString(ed25519.Sign(privateKey, []byte(canonical)))
	return SealEnrollmentPayload(invite.Secret, invite.Offer.InviteID, request)
}

// OpenEnrollmentRequest verifies a request received by the account owner.
func OpenEnrollmentRequest(secret []byte, sealed SealedEnrollmentMessage, nowMillis int64) (EnrollmentRequest, error) {
	plain, err := OpenEnrollmentPayload(secret, sealed)
	if err != nil {
		return EnrollmentRequest{}, err
	}
	var request EnrollmentRequest
	if err := json.Unmarshal(plain, &request); err != nil {
		return EnrollmentRequest{}, errors.New("enrollment request is not valid JSON")
	}
	normalized, err := normalizeEnrollmentRequest(request)
	if err != nil {
		return EnrollmentRequest{}, err
	}
	now := accountNow(nowMillis)
	delta := now - normalized.RequestedAt
	if delta < 0 {
		delta = -delta
	}
	if delta > EnrollmentMaxRequestAgeMillis {
		return EnrollmentRequest{}, errors.New("enrollment request is outside the accepted window")
	}
	if err := verifyEnrollmentRequestSignature(normalized); err != nil {
		return EnrollmentRequest{}, err
	}
	return normalized, nil
}

// IssueEnrollmentCertificate signs a device certificate for an accepted request.
// The certificate deliberately omits staticX25519PublicKey so the signed payload
// matches the one validated by the desktop client.
func IssueEnrollmentCertificate(
	accountSecretKey []byte,
	request EnrollmentRequest,
	epoch int64,
	issuedAt int64,
	ttlMillis int64,
) (AccountDeviceCertificate, error) {
	privateKey, err := parsePrivateKey(accountSecretKey)
	if err != nil {
		return AccountDeviceCertificate{}, err
	}
	if epoch < 0 {
		return AccountDeviceCertificate{}, errors.New("certificate epoch must not be negative")
	}
	if request.AccountUpeerID != upeerIDFromPublicKey(privateKey.Public().(ed25519.PublicKey)) {
		return AccountDeviceCertificate{}, errors.New("enrollment request belongs to another account")
	}
	deviceID, err := DeriveDeviceID(mustDecodeAccountHex(request.DevicePublicKey))
	if err != nil {
		return AccountDeviceCertificate{}, err
	}
	if !strings.EqualFold(deviceID, request.DeviceID) {
		return AccountDeviceCertificate{}, errors.New("enrollment request deviceId does not match its key")
	}
	issuer := accountNow(issuedAt)
	if ttlMillis <= 0 {
		ttlMillis = EnrollmentCertificateTTLMillis
	}
	if ttlMillis > EnrollmentCertificateTTLMillis {
		ttlMillis = EnrollmentCertificateTTLMillis
	}
	certificate := AccountDeviceCertificate{
		Version:          EnrollmentVersion,
		AccountUpeerID:   request.AccountUpeerID,
		AccountPublicKey: accountPublicKeyHex(privateKey.Public().(ed25519.PublicKey)),
		DeviceID:         request.DeviceID,
		DevicePublicKey:  request.DevicePublicKey,
		Platform:         request.Platform,
		Capabilities:     append([]string(nil), request.Capabilities...),
		IssuedAt:         issuer,
		ExpiresAt:        issuer + ttlMillis,
		Epoch:            epoch,
	}
	canonical, err := CanonicalStringify(certificateSignedData(certificate))
	if err != nil {
		return AccountDeviceCertificate{}, fmt.Errorf("canonicalize device certificate: %w", err)
	}
	certificate.Signature = hex.EncodeToString(ed25519.Sign(privateKey, []byte(canonical)))
	return certificate, nil
}

// SealEnrollmentCertificate encrypts the issued certificate with the invitation secret.
func SealEnrollmentCertificate(secret []byte, certificate AccountDeviceCertificate) (SealedEnrollmentMessage, error) {
	inviteID, err := InviteIDForSecret(secret)
	if err != nil {
		return SealedEnrollmentMessage{}, err
	}
	payload := map[string]any{
		"version":     accountNumber(int64(EnrollmentVersion)),
		"certificate": certificate,
	}
	return SealEnrollmentPayload(secret, inviteID, payload)
}

// OpenEnrollmentCertificate decrypts and verifies the certificate returned by the account owner.
func OpenEnrollmentCertificate(
	secret []byte,
	sealed SealedEnrollmentMessage,
	expectedAccountUpeerID string,
	nowMillis int64,
) (AccountDeviceCertificate, error) {
	expectedInviteID, err := InviteIDForSecret(secret)
	if err != nil {
		return AccountDeviceCertificate{}, err
	}
	if !strings.EqualFold(sealed.InviteID, expectedInviteID) {
		return AccountDeviceCertificate{}, errors.New("enrollment response belongs to another invitation")
	}
	plain, err := OpenEnrollmentPayload(secret, sealed)
	if err != nil {
		return AccountDeviceCertificate{}, err
	}
	var envelope struct {
		Version     int                      `json:"version"`
		Certificate AccountDeviceCertificate `json:"certificate"`
	}
	if err := json.Unmarshal(plain, &envelope); err != nil {
		return AccountDeviceCertificate{}, errors.New("enrollment response is not valid JSON")
	}
	if envelope.Version != EnrollmentVersion {
		return AccountDeviceCertificate{}, errors.New("unsupported enrollment response version")
	}
	encoded, err := json.Marshal(envelope.Certificate)
	if err != nil {
		return AccountDeviceCertificate{}, fmt.Errorf("decode enrollment certificate: %w", err)
	}
	certificate, err := VerifyDeviceCertificate(string(encoded), expectedAccountUpeerID, accountNow(nowMillis))
	if err != nil {
		return AccountDeviceCertificate{}, err
	}
	if certificate.StaticX25519PublicKey != "" {
		return AccountDeviceCertificate{}, errors.New("enrollment certificate must not pin a static key")
	}
	return certificate, nil
}

func normalizeEnrollmentRequest(request EnrollmentRequest) (EnrollmentRequest, error) {
	if request.Version != EnrollmentVersion {
		return EnrollmentRequest{}, errors.New("unsupported enrollment request version")
	}
	inviteID := strings.ToLower(strings.TrimSpace(request.InviteID))
	if !validHex(inviteID, 32) {
		return EnrollmentRequest{}, errors.New("request inviteId must be a 32-byte hexadecimal value")
	}
	accountUpeerID, err := normalizeAccountUpeerID(request.AccountUpeerID)
	if err != nil {
		return EnrollmentRequest{}, err
	}
	devicePublicKey, err := normalizeDevicePublicKey(request.DevicePublicKey)
	if err != nil {
		return EnrollmentRequest{}, err
	}
	deviceID, err := DeriveDeviceID(mustDecodeAccountHex(devicePublicKey))
	if err != nil {
		return EnrollmentRequest{}, err
	}
	if !strings.EqualFold(request.DeviceID, deviceID) {
		return EnrollmentRequest{}, errors.New("request deviceId does not match its key")
	}
	platform, err := normalizeEnrollmentPlatform(request.Platform)
	if err != nil {
		return EnrollmentRequest{}, err
	}
	capabilities, err := normalizeEnrollmentCapabilities(request.Capabilities)
	if err != nil {
		return EnrollmentRequest{}, err
	}
	if !validHex(request.DeviceSignature, ed25519.SignatureSize) {
		return EnrollmentRequest{}, errors.New("request deviceSignature is invalid")
	}
	if request.RequestedAt <= 0 {
		return EnrollmentRequest{}, errors.New("request must declare requestedAt")
	}
	return EnrollmentRequest{
		Version:         EnrollmentVersion,
		InviteID:        inviteID,
		AccountUpeerID:  accountUpeerID,
		DeviceID:        deviceID,
		DevicePublicKey: devicePublicKey,
		Platform:        platform,
		Capabilities:    capabilities,
		RequestedAt:     request.RequestedAt,
		DeviceSignature: strings.ToLower(request.DeviceSignature),
	}, nil
}

func verifyEnrollmentRequestSignature(request EnrollmentRequest) error {
	canonical, err := CanonicalStringify(enrollmentRequestPayload(request))
	if err != nil {
		return fmt.Errorf("canonicalize enrollment request: %w", err)
	}
	signature, err := hex.DecodeString(request.DeviceSignature)
	if err != nil {
		return errors.New("request deviceSignature is invalid")
	}
	if !ed25519.Verify(ed25519.PublicKey(mustDecodeAccountHex(request.DevicePublicKey)), []byte(canonical), signature) {
		return errors.New("request deviceSignature does not verify")
	}
	return nil
}

func enrollmentRequestPayload(request EnrollmentRequest) map[string]any {
	return map[string]any{
		"version":         accountNumber(int64(request.Version)),
		"inviteId":        request.InviteID,
		"accountUpeerId":  request.AccountUpeerID,
		"deviceId":        request.DeviceID,
		"devicePublicKey": request.DevicePublicKey,
		"platform":        request.Platform,
		"capabilities":    accountStrings(request.Capabilities),
		"requestedAt":     accountNumber(request.RequestedAt),
	}
}

func normalizeEnrollmentPlatform(value string) (string, error) {
	normalized := strings.ToLower(strings.TrimSpace(value))
	if normalized == "" || len(normalized) > enrollmentMaxPlatformLength {
		return "", errors.New("enrollment platform must be between 1 and 32 characters")
	}
	for _, character := range normalized {
		if character < 0x20 || character == 0x7f {
			return "", errors.New("enrollment platform must not contain control characters")
		}
	}
	return normalized, nil
}

func normalizeEnrollmentCapabilities(values []string) ([]string, error) {
	if len(values) == 0 {
		return nil, errors.New("enrollment capabilities must not be empty")
	}
	seen := make(map[string]struct{}, len(values))
	normalized := make([]string, 0, len(values))
	for _, value := range values {
		capability := strings.ToLower(strings.TrimSpace(value))
		if !isEnrollmentCapability(capability) {
			return nil, fmt.Errorf("unsupported enrollment capability %q", value)
		}
		if _, exists := seen[capability]; exists {
			continue
		}
		seen[capability] = struct{}{}
		normalized = append(normalized, capability)
	}
	if len(normalized) == 0 {
		return nil, errors.New("enrollment capabilities must not be empty")
	}
	return normalized, nil
}

func isEnrollmentCapability(value string) bool {
	for _, candidate := range enrollmentCapabilities {
		if candidate == value {
			return true
		}
	}
	return false
}

func randomEnrollmentNonce() ([]byte, error) {
	nonce := make([]byte, enrollmentNonceBytes)
	if _, err := rand.Read(nonce); err != nil {
		return nil, fmt.Errorf("generate enrollment nonce: %w", err)
	}
	return nonce, nil
}
