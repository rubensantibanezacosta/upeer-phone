package transportbridge

import (
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"

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
