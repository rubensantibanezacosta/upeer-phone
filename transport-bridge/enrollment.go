package transportbridge

import (
	"crypto/rand"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/upeer/upeer/transport-bridge/dht"
)

const enrollmentDefaultCapabilitiesJSON = `["dht","location"]`

type enrollmentInviteWire struct {
	Offer  dht.EnrollmentOffer `json:"offer"`
	Secret string              `json:"secret"`
	Code   string              `json:"code,omitempty"`
}

// ParseEnrollmentInvite decodes the out-of-band invitation shown by the account
// owner and returns the offer plus the one-time secret for the current session.
func ParseEnrollmentInvite(code string, nowMillis int64) (string, error) {
	invite, err := dht.ParseEnrollmentCode(code, nowMillis)
	if err != nil {
		return "", err
	}
	encoded, err := json.Marshal(enrollmentInviteWire{
		Offer:  invite.Offer,
		Secret: base64.RawURLEncoding.EncodeToString(invite.Secret),
		Code:   invite.Code,
	})
	if err != nil {
		return "", fmt.Errorf("encode enrollment invite: %w", err)
	}
	return string(encoded), nil
}

// BuildEnrollmentRequest signs the device pairing request with this device key
// and seals it with the invitation secret.
func (identity *CryptoIdentity) BuildEnrollmentRequest(inviteJSON, platform string, nowMillis int64) (string, error) {
	invite, err := decodeEnrollmentInvite(inviteJSON)
	if err != nil {
		return "", err
	}
	var capabilities []string
	if err := json.Unmarshal([]byte(enrollmentDefaultCapabilitiesJSON), &capabilities); err != nil {
		return "", fmt.Errorf("decode enrollment capabilities: %w", err)
	}
	sealed, err := dht.BuildSealedEnrollmentRequest(identity.secretKey, invite, platform, capabilities, nowMillis)
	if err != nil {
		return "", err
	}
	encoded, err := json.Marshal(sealed)
	if err != nil {
		return "", fmt.Errorf("encode enrollment request: %w", err)
	}
	return string(encoded), nil
}

// OpenEnrollmentCertificate installs the certificate returned by the account
// owner, verifying that it belongs to the expected account and invitation.
func OpenEnrollmentCertificate(inviteJSON, sealedJSON, expectedAccountUpeerID string, nowMillis int64) (string, error) {
	invite, err := decodeEnrollmentInvite(inviteJSON)
	if err != nil {
		return "", err
	}
	var sealed dht.SealedEnrollmentMessage
	if err := json.Unmarshal([]byte(sealedJSON), &sealed); err != nil {
		return "", fmt.Errorf("decode enrollment response: %w", err)
	}
	certificate, err := dht.OpenEnrollmentCertificate(invite.Secret, sealed, expectedAccountUpeerID, nowMillis)
	if err != nil {
		return "", err
	}
	encoded, err := json.Marshal(certificate)
	if err != nil {
		return "", fmt.Errorf("encode enrollment certificate: %w", err)
	}
	return string(encoded), nil
}

func decodeEnrollmentInvite(inviteJSON string) (dht.EnrollmentInvite, error) {
	var wire enrollmentInviteWire
	if err := json.Unmarshal([]byte(inviteJSON), &wire); err != nil {
		return dht.EnrollmentInvite{}, fmt.Errorf("decode enrollment invite: %w", err)
	}
	secret, err := base64.RawURLEncoding.DecodeString(wire.Secret)
	if err != nil || len(secret) != dht.EnrollmentSecretBytes {
		return dht.EnrollmentInvite{}, errors.New("enrollment invite secret is invalid")
	}
	return dht.EnrollmentInvite{Offer: wire.Offer, Secret: secret, Code: wire.Code}, nil
}

// CreateEnrollmentInvite builds the invitation shown by the account owner. The
// returned JSON carries the public offer, the one-time secret and the transfer
// code that the new device consumes.
func (identity *CryptoIdentity) CreateEnrollmentInvite(ttlMillis, nowMillis int64) (string, error) {
	secret := make([]byte, dht.EnrollmentSecretBytes)
	if _, err := rand.Read(secret); err != nil {
		return "", fmt.Errorf("generate enrollment secret: %w", err)
	}
	inviteID, err := dht.InviteIDForSecret(secret)
	if err != nil {
		return "", err
	}
	deviceID, err := dht.DeriveDeviceID(identity.publicKey)
	if err != nil {
		return "", err
	}
	now := nowMillis
	if now <= 0 {
		now = time.Now().UnixMilli()
	}
	if ttlMillis <= 0 {
		ttlMillis = dht.EnrollmentDefaultTTLMillis
	}
	if ttlMillis > dht.EnrollmentMaxTTLMillis {
		ttlMillis = dht.EnrollmentMaxTTLMillis
	}
	offer := dht.EnrollmentOffer{
		Version:            dht.EnrollmentVersion,
		AccountUpeerID:     identity.UpeerID(),
		AccountPublicKey:   hex.EncodeToString(identity.publicKey),
		AuthorizerDeviceID: deviceID,
		InviteID:           inviteID,
		ExpiresAt:          now + ttlMillis,
	}
	code, err := dht.BuildEnrollmentCode(offer, secret)
	if err != nil {
		return "", err
	}
	encoded, err := json.Marshal(enrollmentInviteWire{
		Offer:  offer,
		Secret: base64.RawURLEncoding.EncodeToString(secret),
		Code:   code,
	})
	if err != nil {
		return "", fmt.Errorf("encode enrollment invite: %w", err)
	}
	return string(encoded), nil
}

// OpenEnrollmentRequest decrypts and verifies the request sent by the new device.
func (identity *CryptoIdentity) OpenEnrollmentRequest(inviteJSON, sealedJSON string, nowMillis int64) (string, error) {
	invite, err := decodeEnrollmentInvite(inviteJSON)
	if err != nil {
		return "", err
	}
	sealed, err := decodeSealedEnrollment(sealedJSON)
	if err != nil {
		return "", err
	}
	if !strings.EqualFold(sealed.InviteID, invite.Offer.InviteID) {
		return "", errors.New("enrollment request belongs to another invitation")
	}
	request, err := dht.OpenEnrollmentRequest(invite.Secret, sealed, nowMillis)
	if err != nil {
		return "", err
	}
	if !strings.EqualFold(request.AccountUpeerID, invite.Offer.AccountUpeerID) {
		return "", errors.New("enrollment request belongs to another account")
	}
	if !strings.EqualFold(request.AccountUpeerID, identity.UpeerID()) {
		return "", errors.New("enrollment request is not addressed to this account")
	}
	encoded, err := json.Marshal(request)
	if err != nil {
		return "", fmt.Errorf("encode enrollment request: %w", err)
	}
	return string(encoded), nil
}

// IssueEnrollmentCertificate signs the certificate for an accepted request and
// seals it with the invitation secret so only the new device can read it.
func (identity *CryptoIdentity) IssueEnrollmentCertificate(inviteJSON, requestJSON string, epoch, issuedAt, ttlMillis int64) (string, error) {
	invite, err := decodeEnrollmentInvite(inviteJSON)
	if err != nil {
		return "", err
	}
	var request dht.EnrollmentRequest
	if err := json.Unmarshal([]byte(requestJSON), &request); err != nil {
		return "", fmt.Errorf("decode enrollment request: %w", err)
	}
	if !strings.EqualFold(request.InviteID, invite.Offer.InviteID) {
		return "", errors.New("enrollment request belongs to another invitation")
	}
	certificate, err := dht.IssueEnrollmentCertificate(identity.secretKey, request, epoch, issuedAt, ttlMillis)
	if err != nil {
		return "", err
	}
	sealed, err := dht.SealEnrollmentCertificate(invite.Secret, certificate)
	if err != nil {
		return "", err
	}
	encoded, err := json.Marshal(sealed)
	if err != nil {
		return "", fmt.Errorf("encode enrollment certificate: %w", err)
	}
	return string(encoded), nil
}

func decodeSealedEnrollment(sealedJSON string) (dht.SealedEnrollmentMessage, error) {
	var sealed dht.SealedEnrollmentMessage
	if err := json.Unmarshal([]byte(sealedJSON), &sealed); err != nil {
		return dht.SealedEnrollmentMessage{}, fmt.Errorf("decode sealed enrollment message: %w", err)
	}
	if strings.TrimSpace(sealed.InviteID) == "" || strings.TrimSpace(sealed.Nonce) == "" || strings.TrimSpace(sealed.Ciphertext) == "" {
		return dht.SealedEnrollmentMessage{}, errors.New("sealed enrollment message is incomplete")
	}
	return sealed, nil
}
