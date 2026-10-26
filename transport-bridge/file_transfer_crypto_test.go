package transportbridge

import (
	"bytes"
	"crypto/ed25519"
	"crypto/rand"
	"encoding/base64"
	"encoding/hex"
	"testing"
)

func newFileTestIdentity(t *testing.T) *CryptoIdentity {
	t.Helper()
	seed := make([]byte, ed25519.SeedSize)
	if _, err := rand.Read(seed); err != nil {
		t.Fatalf("seed: %v", err)
	}
	identity, err := NewCryptoIdentity(ed25519.NewKeyFromSeed(seed))
	if err != nil {
		t.Fatalf("identity: %v", err)
	}
	return identity
}

func TestOpenFileKeyReturnsTheSealedAESKey(t *testing.T) {
	identity := newFileTestIdentity(t)
	fileKey := make([]byte, fileKeySize)
	if _, err := rand.Read(fileKey); err != nil {
		t.Fatalf("key: %v", err)
	}

	recipientPublicKey, err := hex.DecodeString(identity.PublicKeyHex())
	if err != nil {
		t.Fatalf("public key: %v", err)
	}
	sealed, err := sealBox(fileKey, recipientPublicKey)
	if err != nil {
		t.Fatalf("seal: %v", err)
	}

	opened, err := identity.OpenFileKey(hex.EncodeToString(sealed))
	if err != nil {
		t.Fatalf("OpenFileKey: %v", err)
	}
	if !bytes.Equal(opened, fileKey) {
		t.Fatal("la clave abierta no coincide")
	}
}

func TestOpenFileKeyFailsClosedForAnotherIdentity(t *testing.T) {
	sender := newFileTestIdentity(t)
	recipient := newFileTestIdentity(t)

	fileKey := bytes.Repeat([]byte{7}, fileKeySize)
	publicKey, err := hex.DecodeString(recipient.PublicKeyHex())
	if err != nil {
		t.Fatalf("public key: %v", err)
	}
	sealed, err := sealBox(fileKey, publicKey)
	if err != nil {
		t.Fatalf("seal: %v", err)
	}

	if _, err := sender.OpenFileKey(hex.EncodeToString(sealed)); err == nil {
		t.Fatal("otra identidad no debería abrir la clave")
	}
}

func TestOpenFileKeyRejectsInvalidInput(t *testing.T) {
	identity := newFileTestIdentity(t)

	if _, err := identity.OpenFileKey("no-hex"); err == nil {
		t.Fatal("se esperaba error con hexadecimal inválido")
	}
	if _, err := identity.OpenFileKey(""); err == nil {
		t.Fatal("se esperaba error con clave vacía")
	}
	if _, err := identity.OpenFileKey("00ff"); err == nil {
		t.Fatal("se esperaba error con sobre demasiado corto")
	}
}

func TestOpenFileKeyFailsWithoutRootSecret(t *testing.T) {
	identity := newFileTestIdentity(t)
	deviceOnly, err := NewDeviceOnlyCryptoIdentity(identity.PublicKeyHex())
	if err != nil {
		t.Fatalf("device only: %v", err)
	}

	if _, err := deviceOnly.OpenFileKey("00ff"); err == nil {
		t.Fatal("un dispositivo sin raíz no puede abrir la clave")
	}
}

func TestDecryptFileChunkRoundTrip(t *testing.T) {
	identity := newFileTestIdentity(t)
	key := bytes.Repeat([]byte{3}, fileKeySize)
	iv := make([]byte, fileChunkIVSize)
	if _, err := rand.Read(iv); err != nil {
		t.Fatalf("iv: %v", err)
	}
	plaintext := bytes.Repeat([]byte("upeer"), 1000)

	data, tag, err := encryptFileChunk(key, plaintext, iv)
	if err != nil {
		t.Fatalf("encrypt: %v", err)
	}
	decrypted, err := identity.DecryptFileChunk(key, hex.EncodeToString(iv), hex.EncodeToString(tag), base64.StdEncoding.EncodeToString(data))
	if err != nil {
		t.Fatalf("DecryptFileChunk: %v", err)
	}
	if !bytes.Equal(decrypted, plaintext) {
		t.Fatal("el trozo descifrado no coincide")
	}
}

func TestDecryptFileChunkRejectsTampering(t *testing.T) {
	identity := newFileTestIdentity(t)
	key := bytes.Repeat([]byte{5}, fileKeySize)
	iv := bytes.Repeat([]byte{1}, fileChunkIVSize)
	plaintext := []byte("contenido")

	data, tag, err := encryptFileChunk(key, plaintext, iv)
	if err != nil {
		t.Fatalf("encrypt: %v", err)
	}
	tampered := append([]byte(nil), data...)
	tampered[0] ^= 0xff

	if _, err := identity.DecryptFileChunk(key, hex.EncodeToString(iv), hex.EncodeToString(tag), base64.StdEncoding.EncodeToString(tampered)); err == nil {
		t.Fatal("un trozo manipulado no debe descifrarse")
	}
}

func TestDecryptFileChunkRejectsInvalidArguments(t *testing.T) {
	identity := newFileTestIdentity(t)
	key := bytes.Repeat([]byte{5}, fileKeySize)
	iv := hex.EncodeToString(bytes.Repeat([]byte{1}, fileChunkIVSize))
	tag := hex.EncodeToString(bytes.Repeat([]byte{2}, fileChunkTagSize))
	data := base64.StdEncoding.EncodeToString([]byte("x"))

	cases := []struct {
		name string
		key  []byte
		iv   string
		tag  string
		data string
	}{
		{"clave corta", []byte{1, 2, 3}, iv, tag, data},
		{"iv no hex", key, "zz", tag, data},
		{"iv corto", key, "0102", tag, data},
		{"tag corto", key, iv, "0102", data},
		{"data no base64", key, iv, tag, "%%%"},
		{"data vacía", key, iv, tag, ""},
	}
	for _, testCase := range cases {
		if _, err := identity.DecryptFileChunk(testCase.key, testCase.iv, testCase.tag, testCase.data); err == nil {
			t.Fatalf("%s: se esperaba error", testCase.name)
		}
	}
}

func TestDeviceIdentityOpensTheDeviceSealedFileKey(t *testing.T) {
	account := newFileTestIdentity(t)
	device := newFileTestIdentity(t)
	fileKey := bytes.Repeat([]byte{6}, fileKeySize)

	accountPublicKey, err := hex.DecodeString(account.PublicKeyHex())
	if err != nil {
		t.Fatalf("account public key: %v", err)
	}
	devicePublicKey, err := hex.DecodeString(device.PublicKeyHex())
	if err != nil {
		t.Fatalf("device public key: %v", err)
	}

	sealedToAccount, err := sealBox(fileKey, accountPublicKey)
	if err != nil {
		t.Fatalf("seal to account: %v", err)
	}
	sealedToDevice, err := sealBox(fileKey, devicePublicKey)
	if err != nil {
		t.Fatalf("seal to device: %v", err)
	}

	if _, err := device.OpenFileKey(hex.EncodeToString(sealedToAccount)); err == nil {
		t.Fatal("la identidad de dispositivo no debería abrir el sobre de cuenta")
	}

	opened, err := device.OpenFileKey(hex.EncodeToString(sealedToDevice))
	if err != nil {
		t.Fatalf("OpenFileKey con clave de dispositivo: %v", err)
	}
	if !bytes.Equal(opened, fileKey) {
		t.Fatal("la clave abierta por el dispositivo no coincide")
	}

	iv := bytes.Repeat([]byte{3}, fileChunkIVSize)
	plaintext := []byte("contenido del adjunto")
	data, tag, err := encryptFileChunk(opened, plaintext, iv)
	if err != nil {
		t.Fatalf("encryptFileChunk: %v", err)
	}
	decrypted, err := device.DecryptFileChunk(
		opened,
		hex.EncodeToString(iv),
		hex.EncodeToString(tag),
		base64.StdEncoding.EncodeToString(data),
	)
	if err != nil {
		t.Fatalf("DecryptFileChunk: %v", err)
	}
	if !bytes.Equal(decrypted, plaintext) {
		t.Fatal("el trozo descifrado no coincide")
	}
}
