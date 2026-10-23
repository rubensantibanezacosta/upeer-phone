package dht

import (
	"crypto/ed25519"
	"encoding/hex"
	"encoding/json"
	"os"
	"strings"
	"testing"
)

type enrollmentVector struct {
	GeneratedBy          string          `json:"generatedBy"`
	Protocol             string          `json:"protocol"`
	Now                  int64           `json:"now"`
	AccountSecretKeySeed string          `json:"accountSecretKeySeed"`
	AccountPublicKey     string          `json:"accountPublicKey"`
	AccountUpeerID       string          `json:"accountUpeerId"`
	DeviceSecretKeySeed  string          `json:"deviceSecretKeySeed"`
	DevicePublicKey      string          `json:"devicePublicKey"`
	Secret               string          `json:"secret"`
	AuthorizerDeviceID   string          `json:"authorizerDeviceId"`
	Code                 string          `json:"code"`
	Offer                EnrollmentOffer `json:"offer"`
	DesktopRequest       json.RawMessage `json:"desktopRequest"`
	DesktopResponse      json.RawMessage `json:"desktopResponse"`
	GoRequest            json.RawMessage `json:"goRequest"`
	GoPlatform           string          `json:"goPlatform"`
	GoCapabilities       []string        `json:"goCapabilities"`
	GoResponse           json.RawMessage `json:"goResponse"`
	Certificate          json.RawMessage `json:"certificate"`
}

func loadEnrollmentVector(t *testing.T) enrollmentVector {
	t.Helper()
	raw, err := os.ReadFile("../testdata/enrollment_vectors.json")
	if err != nil {
		t.Fatal(err)
	}
	var vector enrollmentVector
	if err := json.Unmarshal(raw, &vector); err != nil {
		t.Fatal(err)
	}
	if vector.GeneratedBy != "upper-desktop" {
		t.Fatalf("unexpected vector generator %q", vector.GeneratedBy)
	}
	if vector.Protocol != "b10-enrollment" {
		t.Fatalf("unexpected vector protocol %q", vector.Protocol)
	}
	return vector
}

func enrollmentVectorSecret(t *testing.T, vector enrollmentVector) []byte {
	t.Helper()
	secret, err := hex.DecodeString(vector.Secret)
	if err != nil || len(secret) != EnrollmentSecretBytes {
		t.Fatalf("invalid vector secret: %v", err)
	}
	return secret
}

func enrollmentVectorDeviceKey(t *testing.T, vector enrollmentVector) ed25519.PrivateKey {
	t.Helper()
	seed, err := hex.DecodeString(vector.DeviceSecretKeySeed)
	if err != nil || len(seed) != ed25519.SeedSize {
		t.Fatalf("invalid device seed: %v", err)
	}
	return ed25519.NewKeyFromSeed(seed)
}

func TestParseEnrollmentCodeFromDesktopVector(t *testing.T) {
	vector := loadEnrollmentVector(t)

	invite, err := ParseEnrollmentCode(vector.Code, vector.Now)
	if err != nil {
		t.Fatal(err)
	}
	if invite.Offer.InviteID != vector.Offer.InviteID {
		t.Fatalf("inviteId = %q, want %q", invite.Offer.InviteID, vector.Offer.InviteID)
	}
	if invite.Offer.AccountUpeerID != vector.AccountUpeerID {
		t.Fatalf("accountUpeerId = %q, want %q", invite.Offer.AccountUpeerID, vector.AccountUpeerID)
	}
	if invite.Offer.AccountPublicKey != vector.AccountPublicKey {
		t.Fatalf("accountPublicKey = %q, want %q", invite.Offer.AccountPublicKey, vector.AccountPublicKey)
	}
	if invite.Offer.AuthorizerDeviceID != vector.AuthorizerDeviceID {
		t.Fatalf("authorizerDeviceId = %q, want %q", invite.Offer.AuthorizerDeviceID, vector.AuthorizerDeviceID)
	}
	if invite.Offer.ExpiresAt != vector.Offer.ExpiresAt {
		t.Fatalf("expiresAt = %d, want %d", invite.Offer.ExpiresAt, vector.Offer.ExpiresAt)
	}
	if got, want := hex.EncodeToString(invite.Secret), vector.Secret; got != want {
		t.Fatalf("secret = %q, want %q", got, want)
	}

	if _, err := ParseEnrollmentCode(vector.Code, vector.Offer.ExpiresAt); err == nil {
		t.Fatal("expected expired invitation to be rejected")
	}
}

func TestOpenDesktopEnrollmentRequest(t *testing.T) {
	vector := loadEnrollmentVector(t)
	secret := enrollmentVectorSecret(t, vector)

	var sealed SealedEnrollmentMessage
	if err := json.Unmarshal(vector.DesktopRequest, &sealed); err != nil {
		t.Fatal(err)
	}
	request, err := OpenEnrollmentRequest(secret, sealed, vector.Now)
	if err != nil {
		t.Fatal(err)
	}
	if request.DevicePublicKey != vector.DevicePublicKey {
		t.Fatalf("devicePublicKey = %q, want %q", request.DevicePublicKey, vector.DevicePublicKey)
	}
	if request.AccountUpeerID != vector.AccountUpeerID {
		t.Fatalf("accountUpeerId = %q, want %q", request.AccountUpeerID, vector.AccountUpeerID)
	}
	if request.InviteID != vector.Offer.InviteID {
		t.Fatalf("inviteId = %q, want %q", request.InviteID, vector.Offer.InviteID)
	}
	if request.Platform != "android" {
		t.Fatalf("platform = %q, want android", request.Platform)
	}
	if strings.Join(request.Capabilities, ",") != "dht,location" {
		t.Fatalf("capabilities = %v", request.Capabilities)
	}
	if request.RequestedAt != vector.Now {
		t.Fatalf("requestedAt = %d, want %d", request.RequestedAt, vector.Now)
	}
}

func TestOpenEnrollmentCertificateFromDesktop(t *testing.T) {
	vector := loadEnrollmentVector(t)
	secret := enrollmentVectorSecret(t, vector)

	for name, raw := range map[string]json.RawMessage{
		"desktopRequest": vector.DesktopResponse,
		"goRequest":      vector.GoResponse,
	} {
		var sealed SealedEnrollmentMessage
		if err := json.Unmarshal(raw, &sealed); err != nil {
			t.Fatalf("%s: %v", name, err)
		}
		certificate, err := OpenEnrollmentCertificate(secret, sealed, vector.AccountUpeerID, vector.Now)
		if err != nil {
			t.Fatalf("%s: %v", name, err)
		}
		if certificate.DevicePublicKey != vector.DevicePublicKey {
			t.Fatalf("%s: devicePublicKey = %q", name, certificate.DevicePublicKey)
		}
		if certificate.DeviceID != mustEnrollmentDeviceID(t, vector.DevicePublicKey) {
			t.Fatalf("%s: deviceId = %q", name, certificate.DeviceID)
		}
		if certificate.StaticX25519PublicKey != "" {
			t.Fatalf("%s: certificate must not pin a static key", name)
		}
	}
}

func TestBuildSealedEnrollmentRequestMatchesDesktopVector(t *testing.T) {
	vector := loadEnrollmentVector(t)
	invite, err := ParseEnrollmentCode(vector.Code, vector.Now)
	if err != nil {
		t.Fatal(err)
	}
	deviceKey := enrollmentVectorDeviceKey(t, vector)

	sealed, err := BuildSealedEnrollmentRequest(deviceKey, invite, "android", vector.GoCapabilities, vector.Now)
	if err != nil {
		t.Fatal(err)
	}
	if sealed.InviteID != invite.Offer.InviteID {
		t.Fatalf("inviteId = %q, want %q", sealed.InviteID, invite.Offer.InviteID)
	}
	request, err := OpenEnrollmentRequest(invite.Secret, sealed, vector.Now)
	if err != nil {
		t.Fatal(err)
	}
	if request.DevicePublicKey != vector.DevicePublicKey {
		t.Fatalf("devicePublicKey = %q, want %q", request.DevicePublicKey, vector.DevicePublicKey)
	}
	if request.DeviceID != mustEnrollmentDeviceID(t, vector.DevicePublicKey) {
		t.Fatalf("deviceId = %q", request.DeviceID)
	}
}

func TestOpenEnrollmentRequestRejectsTampering(t *testing.T) {
	vector := loadEnrollmentVector(t)
	secret := enrollmentVectorSecret(t, vector)
	var sealed SealedEnrollmentMessage
	if err := json.Unmarshal(vector.DesktopRequest, &sealed); err != nil {
		t.Fatal(err)
	}

	tampered := sealed
	tampered.Ciphertext = flipEnrollmentHex(sealed.Ciphertext)
	if _, err := OpenEnrollmentRequest(secret, tampered, vector.Now); err == nil {
		t.Fatal("expected tampered ciphertext to be rejected")
	}

	if _, err := OpenEnrollmentRequest(secret, sealed, vector.Now+EnrollmentMaxRequestAgeMillis+1); err == nil {
		t.Fatal("expected stale request to be rejected")
	}

	otherSecret := make([]byte, EnrollmentSecretBytes)
	copy(otherSecret, secret)
	otherSecret[0] ^= 0xff
	if _, err := OpenEnrollmentRequest(otherSecret, sealed, vector.Now); err == nil {
		t.Fatal("expected a foreign secret to fail")
	}
}

func TestIssueEnrollmentCertificateRoundTrip(t *testing.T) {
	vector := loadEnrollmentVector(t)
	invite, err := ParseEnrollmentCode(vector.Code, vector.Now)
	if err != nil {
		t.Fatal(err)
	}
	seed, err := hex.DecodeString(vector.AccountSecretKeySeed)
	if err != nil {
		t.Fatal(err)
	}
	accountKey := ed25519.NewKeyFromSeed(seed)
	var sealed SealedEnrollmentMessage
	if err := json.Unmarshal(vector.DesktopRequest, &sealed); err != nil {
		t.Fatal(err)
	}
	request, err := OpenEnrollmentRequest(invite.Secret, sealed, vector.Now)
	if err != nil {
		t.Fatal(err)
	}

	certificate, err := IssueEnrollmentCertificate(accountKey, request, 1, vector.Now, EnrollmentCertificateTTLMillis)
	if err != nil {
		t.Fatal(err)
	}
	if certificate.AccountUpeerID != vector.AccountUpeerID {
		t.Fatalf("accountUpeerId = %q, want %q", certificate.AccountUpeerID, vector.AccountUpeerID)
	}
	if certificate.Epoch != 1 {
		t.Fatalf("epoch = %d, want 1", certificate.Epoch)
	}

	sealedResponse, err := SealEnrollmentCertificate(invite.Secret, certificate)
	if err != nil {
		t.Fatal(err)
	}
	opened, err := OpenEnrollmentCertificate(invite.Secret, sealedResponse, vector.AccountUpeerID, vector.Now)
	if err != nil {
		t.Fatal(err)
	}
	if opened.DeviceID != certificate.DeviceID {
		t.Fatalf("deviceId = %q, want %q", opened.DeviceID, certificate.DeviceID)
	}
	if _, err := OpenEnrollmentCertificate(invite.Secret, sealedResponse, vector.AccountUpeerID, certificate.ExpiresAt); err == nil {
		t.Fatal("expected expired certificate to be rejected")
	}
}

func TestBuildSealedEnrollmentRequestRejectsInvalidInput(t *testing.T) {
	vector := loadEnrollmentVector(t)
	invite, err := ParseEnrollmentCode(vector.Code, vector.Now)
	if err != nil {
		t.Fatal(err)
	}
	deviceKey := enrollmentVectorDeviceKey(t, vector)

	if _, err := BuildSealedEnrollmentRequest(deviceKey, invite, "android", []string{"torrent"}, vector.Now); err == nil {
		t.Fatal("expected unsupported capability to be rejected")
	}
	if _, err := BuildSealedEnrollmentRequest(deviceKey, invite, "", []string{"dht"}, vector.Now); err == nil {
		t.Fatal("expected empty platform to be rejected")
	}
	if _, err := BuildSealedEnrollmentRequest(deviceKey[:16], invite, "android", []string{"dht"}, vector.Now); err == nil {
		t.Fatal("expected short device key to be rejected")
	}
}

func mustEnrollmentDeviceID(t *testing.T, publicKeyHex string) string {
	t.Helper()
	deviceID, err := DeriveDeviceID(mustDecodeAccountHex(publicKeyHex))
	if err != nil {
		t.Fatal(err)
	}
	return deviceID
}

func flipEnrollmentHex(value string) string {
	replacement := byte('0')
	if value[0] == '0' {
		replacement = '1'
	}
	return string(replacement) + value[1:]
}
