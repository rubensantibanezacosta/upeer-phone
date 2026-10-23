package transportbridge

import (
	"crypto/ed25519"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"os"
	"testing"

	"github.com/upeer/upeer/transport-bridge/dht"
)

func newTestIdentity(t *testing.T, seedByte byte) *CryptoIdentity {
	t.Helper()
	seed := make([]byte, ed25519.SeedSize)
	for index := range seed {
		seed[index] = seedByte
	}
	identity, err := NewCryptoIdentity(ed25519.NewKeyFromSeed(seed))
	if err != nil {
		t.Fatal(err)
	}
	return identity
}

func TestAuthorizerEnrollsAnotherDevice(t *testing.T) {
	const now int64 = 1_760_000_000_000
	authorizer := newTestIdentity(t, 7)
	newDevice := newTestIdentity(t, 9)

	inviteJSON, err := authorizer.CreateEnrollmentInvite(0, now)
	if err != nil {
		t.Fatal(err)
	}
	var invite enrollmentInviteWire
	if err := json.Unmarshal([]byte(inviteJSON), &invite); err != nil {
		t.Fatal(err)
	}
	if invite.Offer.AccountUpeerID != authorizer.UpeerID() {
		t.Fatalf("offer accountUpeerId = %q, want %q", invite.Offer.AccountUpeerID, authorizer.UpeerID())
	}
	if invite.Code == "" || invite.Secret == "" {
		t.Fatal("invite is missing the transfer code or the secret")
	}
	if invite.Offer.ExpiresAt != now+dht.EnrollmentDefaultTTLMillis {
		t.Fatalf("expiresAt = %d, want the default TTL", invite.Offer.ExpiresAt)
	}
	if invite.Offer.InviteID == "" || invite.Offer.AuthorizerDeviceID == "" {
		t.Fatal("offer is missing inviteId or authorizerDeviceId")
	}

	requestJSON, err := newDevice.BuildEnrollmentRequest(inviteJSON, "desktop", now+1000)
	if err != nil {
		t.Fatal(err)
	}
	openedJSON, err := authorizer.OpenEnrollmentRequest(inviteJSON, requestJSON, now+2000)
	if err != nil {
		t.Fatal(err)
	}
	var request dht.EnrollmentRequest
	if err := json.Unmarshal([]byte(openedJSON), &request); err != nil {
		t.Fatal(err)
	}
	if request.Platform != "desktop" {
		t.Fatalf("platform = %q, want desktop", request.Platform)
	}
	if request.InviteID != invite.Offer.InviteID {
		t.Fatalf("request inviteId = %q", request.InviteID)
	}

	responseJSON, err := authorizer.IssueEnrollmentCertificate(inviteJSON, openedJSON, 4, now+3000, 0)
	if err != nil {
		t.Fatal(err)
	}
	certificateJSON, err := OpenEnrollmentCertificate(inviteJSON, responseJSON, authorizer.UpeerID(), now+4000)
	if err != nil {
		t.Fatal(err)
	}
	var certificate dht.AccountDeviceCertificate
	if err := json.Unmarshal([]byte(certificateJSON), &certificate); err != nil {
		t.Fatal(err)
	}
	expectedDeviceID, err := dht.DeriveDeviceID(mustPublicKey(t, newDevice))
	if err != nil {
		t.Fatal(err)
	}
	if certificate.DeviceID != expectedDeviceID {
		t.Fatalf("certificate deviceId = %q, want %q", certificate.DeviceID, expectedDeviceID)
	}
	if certificate.Epoch != 4 {
		t.Fatalf("certificate epoch = %d, want 4", certificate.Epoch)
	}
	if certificate.AccountUpeerID != authorizer.UpeerID() {
		t.Fatalf("certificate accountUpeerId = %q", certificate.AccountUpeerID)
	}
}

func TestAuthorizerRejectsForeignInviteAndRequest(t *testing.T) {
	const now int64 = 1_760_000_000_000
	authorizer := newTestIdentity(t, 3)
	other := newTestIdentity(t, 5)
	newDevice := newTestIdentity(t, 11)

	inviteJSON, err := authorizer.CreateEnrollmentInvite(0, now)
	if err != nil {
		t.Fatal(err)
	}
	foreignInviteJSON, err := other.CreateEnrollmentInvite(0, now)
	if err != nil {
		t.Fatal(err)
	}
	requestJSON, err := newDevice.BuildEnrollmentRequest(inviteJSON, "desktop", now)
	if err != nil {
		t.Fatal(err)
	}

	if _, err := other.OpenEnrollmentRequest(inviteJSON, requestJSON, now); err == nil {
		t.Fatal("expected a request addressed to another account to be rejected")
	}
	if _, err := authorizer.OpenEnrollmentRequest(foreignInviteJSON, requestJSON, now); err == nil {
		t.Fatal("expected a request sealed with another invitation to be rejected")
	}
	if _, err := authorizer.OpenEnrollmentRequest(inviteJSON, requestJSON, now+dht.EnrollmentMaxRequestAgeMillis+1); err == nil {
		t.Fatal("expected a stale request to be rejected")
	}

	openedJSON, err := authorizer.OpenEnrollmentRequest(inviteJSON, requestJSON, now)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := other.IssueEnrollmentCertificate(inviteJSON, openedJSON, 1, now, 0); err == nil {
		t.Fatal("expected another account to be unable to issue the certificate")
	}
	if _, err := authorizer.IssueEnrollmentCertificate(inviteJSON, openedJSON, -1, now, 0); err == nil {
		t.Fatal("expected a negative epoch to be rejected")
	}
}

func TestAuthorizerCapsInviteTTL(t *testing.T) {
	const now int64 = 1_760_000_000_000
	authorizer := newTestIdentity(t, 13)
	inviteJSON, err := authorizer.CreateEnrollmentInvite(dht.EnrollmentMaxTTLMillis*4, now)
	if err != nil {
		t.Fatal(err)
	}
	var invite enrollmentInviteWire
	if err := json.Unmarshal([]byte(inviteJSON), &invite); err != nil {
		t.Fatal(err)
	}
	if invite.Offer.ExpiresAt != now+dht.EnrollmentMaxTTLMillis {
		t.Fatalf("expiresAt = %d, want the capped TTL", invite.Offer.ExpiresAt)
	}
	secret, err := base64.RawURLEncoding.DecodeString(invite.Secret)
	if err != nil {
		t.Fatal(err)
	}
	if len(secret) != dht.EnrollmentSecretBytes {
		t.Fatalf("secret length = %d", len(secret))
	}
}

func TestDesktopRequestIsIssuedByMobileAuthorizer(t *testing.T) {
	vector := loadEnrollmentBridgeVector(t)
	var vectorFile struct {
		AccountSecretKeySeed string                       `json:"accountSecretKeySeed"`
		DesktopRequest       json.RawMessage              `json:"desktopRequest"`
		Certificate          dht.AccountDeviceCertificate `json:"certificate"`
	}
	raw, err := os.ReadFile("testdata/enrollment_vectors.json")
	if err != nil {
		t.Fatal(err)
	}
	if err := json.Unmarshal(raw, &vectorFile); err != nil {
		t.Fatal(err)
	}
	if len(vectorFile.DesktopRequest) == 0 || vectorFile.Certificate.DeviceID == "" {
		t.Skip("vector does not carry the authorizer direction")
	}
	seed, err := hex.DecodeString(vectorFile.AccountSecretKeySeed)
	if err != nil {
		t.Fatal(err)
	}
	authorizer, err := NewCryptoIdentity(ed25519.NewKeyFromSeed(seed))
	if err != nil {
		t.Fatal(err)
	}
	inviteJSON, err := ParseEnrollmentInvite(vector.Code, vector.Now)
	if err != nil {
		t.Fatal(err)
	}
	openedJSON, err := authorizer.OpenEnrollmentRequest(inviteJSON, string(vectorFile.DesktopRequest), vector.Now)
	if err != nil {
		t.Fatal(err)
	}
	var request dht.EnrollmentRequest
	if err := json.Unmarshal([]byte(openedJSON), &request); err != nil {
		t.Fatal(err)
	}
	if request.DeviceID != vectorFile.Certificate.DeviceID {
		t.Fatalf("request deviceId = %q, want %q", request.DeviceID, vectorFile.Certificate.DeviceID)
	}

	responseJSON, err := authorizer.IssueEnrollmentCertificate(inviteJSON, openedJSON, vectorFile.Certificate.Epoch, vectorFile.Certificate.IssuedAt, 0)
	if err != nil {
		t.Fatal(err)
	}
	certificateJSON, err := OpenEnrollmentCertificate(inviteJSON, responseJSON, vector.AccountUpeerID, vector.Now)
	if err != nil {
		t.Fatal(err)
	}
	var certificate dht.AccountDeviceCertificate
	if err := json.Unmarshal([]byte(certificateJSON), &certificate); err != nil {
		t.Fatal(err)
	}
	if certificate.Signature != vectorFile.Certificate.Signature {
		t.Fatalf("certificate signature diverges from Desktop:\n got %s\nwant %s", certificate.Signature, vectorFile.Certificate.Signature)
	}
	if certificate.ExpiresAt != vectorFile.Certificate.ExpiresAt || certificate.Epoch != vectorFile.Certificate.Epoch {
		t.Fatalf("certificate window diverges: got %d/%d", certificate.ExpiresAt, certificate.Epoch)
	}
}

func mustPublicKey(t *testing.T, identity *CryptoIdentity) []byte {
	t.Helper()
	return identity.publicKey
}

func hexMatches(value string) bool {
	_, err := hex.DecodeString(value)
	return err == nil && value != ""
}
