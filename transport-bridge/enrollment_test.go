package transportbridge

import (
	"crypto/ed25519"
	"encoding/hex"
	"encoding/json"
	"os"
	"testing"
)

type enrollmentBridgeVector struct {
	Now                 int64           `json:"now"`
	AccountUpeerID      string          `json:"accountUpeerId"`
	DeviceSecretKeySeed string          `json:"deviceSecretKeySeed"`
	Code                string          `json:"code"`
	DesktopResponse     json.RawMessage `json:"desktopResponse"`
}

func loadEnrollmentBridgeVector(t *testing.T) enrollmentBridgeVector {
	t.Helper()
	raw, err := os.ReadFile("testdata/enrollment_vectors.json")
	if err != nil {
		t.Fatal(err)
	}
	var vector enrollmentBridgeVector
	if err := json.Unmarshal(raw, &vector); err != nil {
		t.Fatal(err)
	}
	return vector
}

func TestEnrollmentBridgeRoundTrip(t *testing.T) {
	vector := loadEnrollmentBridgeVector(t)

	inviteJSON, err := ParseEnrollmentInvite(vector.Code, vector.Now)
	if err != nil {
		t.Fatal(err)
	}
	var invite enrollmentInviteWire
	if err := json.Unmarshal([]byte(inviteJSON), &invite); err != nil {
		t.Fatal(err)
	}
	if invite.Offer.AccountUpeerID != vector.AccountUpeerID {
		t.Fatalf("accountUpeerId = %q, want %q", invite.Offer.AccountUpeerID, vector.AccountUpeerID)
	}
	if invite.Secret == "" {
		t.Fatal("invite secret was not returned")
	}

	seed, err := hex.DecodeString(vector.DeviceSecretKeySeed)
	if err != nil {
		t.Fatal(err)
	}
	device, err := NewCryptoIdentity(ed25519.NewKeyFromSeed(seed))
	if err != nil {
		t.Fatal(err)
	}
	requestJSON, err := device.BuildEnrollmentRequest(inviteJSON, "android", vector.Now)
	if err != nil {
		t.Fatal(err)
	}
	var request map[string]string
	if err := json.Unmarshal([]byte(requestJSON), &request); err != nil {
		t.Fatal(err)
	}
	if request["inviteId"] != invite.Offer.InviteID {
		t.Fatalf("request inviteId = %q, want %q", request["inviteId"], invite.Offer.InviteID)
	}
	if request["ciphertext"] == "" || request["nonce"] == "" {
		t.Fatal("request was not sealed")
	}

	certificateJSON, err := OpenEnrollmentCertificate(
		inviteJSON,
		string(vector.DesktopResponse),
		vector.AccountUpeerID,
		vector.Now,
	)
	if err != nil {
		t.Fatal(err)
	}
	var certificate struct {
		DeviceID        string `json:"deviceId"`
		AccountUpeerID  string `json:"accountUpeerId"`
		DevicePublicKey string `json:"devicePublicKey"`
		StaticX25519Key string `json:"staticX25519PublicKey"`
	}
	if err := json.Unmarshal([]byte(certificateJSON), &certificate); err != nil {
		t.Fatal(err)
	}
	if certificate.AccountUpeerID != vector.AccountUpeerID {
		t.Fatalf("certificate accountUpeerId = %q", certificate.AccountUpeerID)
	}
	if certificate.DeviceID == "" || certificate.DevicePublicKey == "" {
		t.Fatal("certificate is missing the device identity")
	}
	if certificate.StaticX25519Key != "" {
		t.Fatal("enrollment certificate must not pin a static key")
	}
}

func TestEnrollmentBridgeRejectsForeignAccount(t *testing.T) {
	vector := loadEnrollmentBridgeVector(t)
	inviteJSON, err := ParseEnrollmentInvite(vector.Code, vector.Now)
	if err != nil {
		t.Fatal(err)
	}

	if _, err := OpenEnrollmentCertificate(inviteJSON, string(vector.DesktopResponse), "ff", vector.Now); err == nil {
		t.Fatal("expected a foreign account to be rejected")
	}
	if _, err := ParseEnrollmentInvite(vector.Code, vector.Now+60*60*1000); err == nil {
		t.Fatal("expected an expired invitation to be rejected")
	}
	if _, err := ParseEnrollmentInvite("not-base64!", vector.Now); err == nil {
		t.Fatal("expected an invalid code to be rejected")
	}
}
