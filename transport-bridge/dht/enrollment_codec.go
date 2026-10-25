package dht

import (
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"strings"

	"golang.org/x/crypto/blake2b"
	"golang.org/x/crypto/nacl/secretbox"
)

const (
	EnrollmentVersion                = 1
	EnrollmentSecretBytes            = 32
	EnrollmentDefaultTTLMillis       = 5 * 60 * 1000
	EnrollmentMaxTTLMillis           = 15 * 60 * 1000
	EnrollmentMaxRequestAgeMillis    = 10 * 60 * 1000
	EnrollmentCertificateTTLMillis   = 90 * 24 * 60 * 60 * 1000
	enrollmentNonceBytes             = 24
	enrollmentSecretboxKeyBytes      = 32
	enrollmentSecretboxMACBytes      = 16
	enrollmentMaxCodeLength          = 2048
	enrollmentSecretKeyDerivationTag = "upeer-enrollment"
)

// EnrollmentOffer is the public half of an out-of-band enrollment invitation.
type EnrollmentOffer struct {
	Version            int    `json:"version"`
	AccountUpeerID     string `json:"accountUpeerId"`
	AccountPublicKey   string `json:"accountPublicKey"`
	AuthorizerDeviceID string `json:"authorizerDeviceId"`
	InviteID           string `json:"inviteId"`
	ExpiresAt          int64  `json:"expiresAt"`
	AuthorizerAddress  string `json:"authorizerAddress,omitempty"`
}

// EnrollmentInvite is a parsed invitation: the offer plus the one-time secret.
type EnrollmentInvite struct {
	Offer  EnrollmentOffer
	Secret []byte
	Code   string
}

// SealedEnrollmentMessage is a secretbox-sealed enrollment payload.
type SealedEnrollmentMessage struct {
	InviteID   string `json:"inviteId"`
	Nonce      string `json:"nonce"`
	Ciphertext string `json:"ciphertext"`
}

type enrollmentCodeWire struct {
	Offer  EnrollmentOffer `json:"offer"`
	Secret string          `json:"secret"`
}

// InviteIDForSecret derives the invitation identifier from the one-time secret.
func InviteIDForSecret(secret []byte) (string, error) {
	if len(secret) != EnrollmentSecretBytes {
		return "", errors.New("enrollment secret must be 32 bytes")
	}
	sum := blake2b.Sum256(secret)
	return hex.EncodeToString(sum[:]), nil
}

// BuildEnrollmentCode assembles the base64url invitation code shown to the new device.
func BuildEnrollmentCode(offer EnrollmentOffer, secret []byte) (string, error) {
	inviteID, err := InviteIDForSecret(secret)
	if err != nil {
		return "", err
	}
	if !strings.EqualFold(inviteID, offer.InviteID) {
		return "", errors.New("enrollment inviteId does not match its secret")
	}
	encoded, err := json.Marshal(enrollmentCodeWire{
		Offer:  offer,
		Secret: base64.RawURLEncoding.EncodeToString(secret),
	})
	if err != nil {
		return "", fmt.Errorf("encode enrollment code: %w", err)
	}
	return base64.RawURLEncoding.EncodeToString(encoded), nil
}

// ParseEnrollmentCode decodes an invitation code and verifies it has not expired.
func ParseEnrollmentCode(code string, nowMillis int64) (EnrollmentInvite, error) {
	normalized := strings.TrimSpace(code)
	if normalized == "" || len(normalized) > enrollmentMaxCodeLength {
		return EnrollmentInvite{}, errors.New("enrollment code is empty or too long")
	}
	decoded, err := base64.RawURLEncoding.DecodeString(normalized)
	if err != nil || len(decoded) == 0 {
		return EnrollmentInvite{}, errors.New("enrollment code is not valid base64url")
	}
	var wire enrollmentCodeWire
	if err := json.Unmarshal(decoded, &wire); err != nil {
		return EnrollmentInvite{}, errors.New("enrollment code does not contain valid JSON")
	}
	offer, err := normalizeEnrollmentOffer(wire.Offer)
	if err != nil {
		return EnrollmentInvite{}, err
	}
	if offer.ExpiresAt <= accountNow(nowMillis) {
		return EnrollmentInvite{}, errors.New("enrollment invitation has expired")
	}
	secret, err := base64.RawURLEncoding.DecodeString(wire.Secret)
	if err != nil || len(secret) != EnrollmentSecretBytes {
		return EnrollmentInvite{}, errors.New("enrollment secret is invalid")
	}
	inviteID, err := InviteIDForSecret(secret)
	if err != nil {
		return EnrollmentInvite{}, err
	}
	if inviteID != offer.InviteID {
		return EnrollmentInvite{}, errors.New("enrollment inviteId does not match its secret")
	}
	return EnrollmentInvite{Offer: offer, Secret: secret, Code: normalized}, nil
}

// OpenEnrollmentPayload decrypts a sealed enrollment message with the shared secret.
func OpenEnrollmentPayload(secret []byte, sealed SealedEnrollmentMessage) ([]byte, error) {
	key, err := enrollmentSecretboxKey(secret)
	if err != nil {
		return nil, err
	}
	nonce, err := decodeEnrollmentHex(sealed.Nonce, enrollmentNonceBytes, "nonce")
	if err != nil {
		return nil, err
	}
	if sealed.Ciphertext == "" || len(sealed.Ciphertext)%2 != 0 || !isHexText(sealed.Ciphertext) {
		return nil, errors.New("enrollment ciphertext is invalid")
	}
	box, err := hex.DecodeString(sealed.Ciphertext)
	if err != nil {
		return nil, errors.New("enrollment ciphertext is invalid")
	}
	if len(box) <= enrollmentSecretboxMACBytes {
		return nil, errors.New("enrollment ciphertext is too short")
	}
	var nonceArray [enrollmentNonceBytes]byte
	copy(nonceArray[:], nonce)
	var keyArray [enrollmentSecretboxKeyBytes]byte
	copy(keyArray[:], key)
	plain, ok := secretbox.Open(nil, box, &nonceArray, &keyArray)
	if !ok {
		return nil, errors.New("enrollment message could not be decrypted")
	}
	return plain, nil
}

// SealEnrollmentPayload encrypts a payload with the invitation secret.
func SealEnrollmentPayload(secret []byte, inviteID string, payload any) (SealedEnrollmentMessage, error) {
	key, err := enrollmentSecretboxKey(secret)
	if err != nil {
		return SealedEnrollmentMessage{}, err
	}
	nonce, err := randomEnrollmentNonce()
	if err != nil {
		return SealedEnrollmentMessage{}, err
	}
	plain, err := json.Marshal(payload)
	if err != nil {
		return SealedEnrollmentMessage{}, fmt.Errorf("encode enrollment payload: %w", err)
	}
	var nonceArray [enrollmentNonceBytes]byte
	copy(nonceArray[:], nonce)
	var keyArray [enrollmentSecretboxKeyBytes]byte
	copy(keyArray[:], key)
	box := secretbox.Seal(nil, plain, &nonceArray, &keyArray)
	return SealedEnrollmentMessage{
		InviteID:   inviteID,
		Nonce:      hex.EncodeToString(nonceArray[:]),
		Ciphertext: hex.EncodeToString(box),
	}, nil
}

func enrollmentSecretboxKey(secret []byte) ([]byte, error) {
	if len(secret) != EnrollmentSecretBytes {
		return nil, errors.New("enrollment secret must be 32 bytes")
	}
	digest, err := blake2b.New(enrollmentSecretboxKeyBytes, secret)
	if err != nil {
		return nil, fmt.Errorf("derive enrollment key: %w", err)
	}
	if _, err := digest.Write([]byte(enrollmentSecretKeyDerivationTag)); err != nil {
		return nil, fmt.Errorf("derive enrollment key: %w", err)
	}
	return digest.Sum(nil), nil
}

func normalizeAuthorizerAddress(value string) (string, error) {
	trimmed := strings.ToLower(strings.TrimSpace(value))
	if trimmed == "" {
		return "", nil
	}
	if len(trimmed) > 45 || !strings.Contains(trimmed, ":") {
		return "", errors.New("authorizerAddress must be an IPv6 literal")
	}
	for _, char := range trimmed {
		if (char < '0' || char > '9') && (char < 'a' || char > 'f') && char != ':' && char != '.' {
			return "", errors.New("authorizerAddress must be an IPv6 literal")
		}
	}
	return trimmed, nil
}

func normalizeEnrollmentOffer(offer EnrollmentOffer) (EnrollmentOffer, error) {
	if offer.Version != EnrollmentVersion {
		return EnrollmentOffer{}, errors.New("unsupported enrollment version")
	}
	accountUpeerID, err := normalizeAccountUpeerID(offer.AccountUpeerID)
	if err != nil {
		return EnrollmentOffer{}, err
	}
	accountPublicKey, err := normalizeAccountPublicKey(offer.AccountPublicKey)
	if err != nil {
		return EnrollmentOffer{}, err
	}
	authorizerDeviceID := strings.ToLower(strings.TrimSpace(offer.AuthorizerDeviceID))
	if !validHex(authorizerDeviceID, 32) {
		return EnrollmentOffer{}, errors.New("authorizerDeviceId must be a 32-byte hexadecimal value")
	}
	inviteID := strings.ToLower(strings.TrimSpace(offer.InviteID))
	if !validHex(inviteID, 32) {
		return EnrollmentOffer{}, errors.New("inviteId must be a 32-byte hexadecimal value")
	}
	if offer.ExpiresAt <= 0 {
		return EnrollmentOffer{}, errors.New("enrollment invitation must declare expiresAt")
	}
	authorizerAddress, err := normalizeAuthorizerAddress(offer.AuthorizerAddress)
	if err != nil {
		return EnrollmentOffer{}, err
	}
	return EnrollmentOffer{
		Version:            EnrollmentVersion,
		AccountUpeerID:     accountUpeerID,
		AccountPublicKey:   accountPublicKey,
		AuthorizerDeviceID: authorizerDeviceID,
		InviteID:           inviteID,
		ExpiresAt:          offer.ExpiresAt,
		AuthorizerAddress:  authorizerAddress,
	}, nil
}

func decodeEnrollmentHex(value string, expectedBytes int, field string) ([]byte, error) {
	if !isHexText(value) || len(value) != expectedBytes*2 {
		return nil, fmt.Errorf("enrollment %s must be a %d-byte hexadecimal value", field, expectedBytes)
	}
	decoded, err := hex.DecodeString(value)
	if err != nil {
		return nil, fmt.Errorf("enrollment %s is invalid", field)
	}
	return decoded, nil
}

func isHexText(value string) bool {
	if value == "" {
		return false
	}
	for _, character := range value {
		isDigit := character >= '0' && character <= '9'
		isLower := character >= 'a' && character <= 'f'
		isUpper := character >= 'A' && character <= 'F'
		if !isDigit && !isLower && !isUpper {
			return false
		}
	}
	return true
}
