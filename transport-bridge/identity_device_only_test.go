package transportbridge

import (
	"crypto/ed25519"
	"encoding/hex"
	"encoding/json"
	"errors"
	"strings"
	"testing"
)

func rootIdentity(t *testing.T) *CryptoIdentity {
	t.Helper()
	seed := make([]byte, ed25519.SeedSize)
	for i := range seed {
		seed[i] = byte(i + 1)
	}
	identity, err := NewCryptoIdentity(ed25519.NewKeyFromSeed(seed))
	if err != nil {
		t.Fatalf("NewCryptoIdentity: %v", err)
	}
	return identity
}

func deviceOnlyIdentity(t *testing.T) (*CryptoIdentity, *CryptoIdentity) {
	t.Helper()
	root := rootIdentity(t)
	device, err := NewDeviceOnlyCryptoIdentity(root.PublicKeyHex())
	if err != nil {
		t.Fatalf("NewDeviceOnlyCryptoIdentity: %v", err)
	}
	return root, device
}

func assertRootUnavailable(t *testing.T, operation string, err error) {
	t.Helper()
	if err == nil {
		t.Fatalf("%s expected an error in device-only mode", operation)
	}
	if !errors.Is(err, ErrRootIdentityUnavailable) {
		t.Fatalf("%s returned %v, want ErrRootIdentityUnavailable", operation, err)
	}
}

func TestDeviceOnlyIdentityRejectsInvalidAccountKey(t *testing.T) {
	cases := []string{"", "zz", strings.Repeat("a", 62), strings.Repeat("a", 66), "no-es-hex" + strings.Repeat("a", 56)}
	for _, candidate := range cases {
		if _, err := NewDeviceOnlyCryptoIdentity(candidate); err == nil {
			t.Fatalf("accepted invalid account public key %q", candidate)
		}
	}
}

func TestDeviceOnlyIdentityKeepsAccountKeysWithoutSecret(t *testing.T) {
	root, device := deviceOnlyIdentity(t)
	if !device.IsDeviceOnly() {
		t.Fatal("device-only identity reports a root secret")
	}
	if root.IsDeviceOnly() {
		t.Fatal("root identity reports device-only mode")
	}
	if device.PublicKeyHex() != root.PublicKeyHex() {
		t.Fatalf("public key %s, want %s", device.PublicKeyHex(), root.PublicKeyHex())
	}
	if device.UpeerID() != root.UpeerID() {
		t.Fatalf("upeerId %s, want %s", device.UpeerID(), root.UpeerID())
	}
}

func TestDeviceOnlyIdentityAcceptsUppercaseAccountKey(t *testing.T) {
	root := rootIdentity(t)
	device, err := NewDeviceOnlyCryptoIdentity(strings.ToUpper(root.PublicKeyHex()))
	if err != nil {
		t.Fatalf("uppercase account key rejected: %v", err)
	}
	if device.PublicKeyHex() != root.PublicKeyHex() {
		t.Fatalf("public key %s, want %s", device.PublicKeyHex(), root.PublicKeyHex())
	}
}

func TestDeviceOnlyIdentityFailsClosedOnSignature(t *testing.T) {
	_, device := deviceOnlyIdentity(t)
	if signature := device.Sign([]byte("payload")); signature != "" {
		t.Fatalf("device-only identity produced a root signature: %s", signature)
	}
	_, err := device.SignCanonicalJSON(`{"a":1}`)
	assertRootUnavailable(t, "SignCanonicalJSON", err)
	_, err = device.BuildSignedPacket("200::1", `{"type":"PING"}`)
	assertRootUnavailable(t, "BuildSignedPacket", err)
	_, err = device.BuildAck("200::1", "message-1", "delivered")
	assertRootUnavailable(t, "BuildAck", err)
}

func TestDeviceOnlyIdentityFailsClosedOnSealedBoxes(t *testing.T) {
	root, device := deviceOnlyIdentity(t)
	sealed, err := root.Seal([]byte("hello"), root.PublicKeyHex())
	if err != nil {
		t.Fatalf("Seal: %v", err)
	}
	if _, err := device.OpenSealed(sealed); !errors.Is(err, ErrRootIdentityUnavailable) {
		t.Fatalf("OpenSealed returned %v, want ErrRootIdentityUnavailable", err)
	}
}

func TestDeviceOnlyIdentityFailsClosedOnHandshake(t *testing.T) {
	_, device := deviceOnlyIdentity(t)
	_, err := device.CreateSignedPreKey(1)
	assertRootUnavailable(t, "CreateSignedPreKey", err)
}

func TestDeviceOnlyIdentityFailsClosedOnSessions(t *testing.T) {
	_, device := deviceOnlyIdentity(t)
	peer := rootIdentity(t)
	spk, err := peer.CreateSignedPreKey(1)
	if err != nil {
		t.Fatalf("CreateSignedPreKey: %v", err)
	}
	_, err = device.StartOutboundSession(peer.PublicKeyHex(), spkPublicKeyHex(t, spk), 1)
	assertRootUnavailable(t, "StartOutboundSession", err)
}

func TestDeviceOnlyIdentityFailsClosedOnRegistryAndEnrollment(t *testing.T) {
	_, device := deviceOnlyIdentity(t)
	_, err := device.BuildAccountRegistryFromCertificates(`[]`, `[]`, 1, 1, 1000)
	assertRootUnavailable(t, "BuildAccountRegistryFromCertificates", err)
	_, err = device.RevokeAccountDevice(`{}`, strings.Repeat("a", 64), "test", 1)
	assertRootUnavailable(t, "RevokeAccountDevice", err)
	_, err = device.ApproveDevicePairingRequest(`{}`, 1, 1, 2)
	assertRootUnavailable(t, "ApproveDevicePairingRequest", err)
	_, err = device.IssueEnrollmentCertificate(`{}`, `{}`, 1, 1, 1000)
	assertRootUnavailable(t, "IssueEnrollmentCertificate", err)
}

func TestDeviceOnlyIdentityFailsClosedOnLocationBlocks(t *testing.T) {
	_, device := deviceOnlyIdentity(t)
	_, err := device.BuildLocationBlock(`["200::1"]`, 1, 1000, strings.Repeat("a", 64))
	assertRootUnavailable(t, "BuildLocationBlock", err)
}

func TestRootIdentityStillSignsAndVerifies(t *testing.T) {
	root := rootIdentity(t)
	signature, err := root.SignCanonicalJSON(`{"a":1}`)
	if err != nil {
		t.Fatalf("SignCanonicalJSON: %v", err)
	}
	if _, err := hex.DecodeString(signature); err != nil {
		t.Fatalf("signature is not hexadecimal: %v", err)
	}
	ok, err := VerifyCanonicalJSON(`{"a":1}`, signature, root.PublicKeyHex())
	if err != nil {
		t.Fatalf("VerifyCanonicalJSON: %v", err)
	}
	if !ok {
		t.Fatal("root signature did not verify")
	}
}

func spkPublicKeyHex(t *testing.T, signedPreKeyJSON string) string {
	t.Helper()
	var state signedPreKeyState
	if err := json.Unmarshal([]byte(signedPreKeyJSON), &state); err != nil {
		t.Fatalf("decode signed pre-key: %v", err)
	}
	return state.SPKPub
}
