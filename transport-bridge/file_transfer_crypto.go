package transportbridge

import (
	"crypto/aes"
	"crypto/cipher"
	"crypto/ed25519"
	"encoding/base64"
	"encoding/hex"
	"errors"
	"fmt"
	"strings"
)

const (
	fileKeySize      = 32
	fileChunkIVSize  = 12
	fileChunkTagSize = 16
	maxFileChunkSize = 64 * 1024
)

func (identity *CryptoIdentity) OpenFileKey(encryptedKeyHex string) ([]byte, error) {
	secretKey, err := identity.requireSecretKey("openFileKey")
	if err != nil {
		return nil, err
	}
	return openFileKeyWithKey(encryptedKeyHex, secretKey)
}

func openFileKeyWithKey(encryptedKeyHex string, secretKey ed25519.PrivateKey) ([]byte, error) {
	raw, err := hex.DecodeString(strings.TrimSpace(encryptedKeyHex))
	if err != nil {
		return nil, errors.New("la clave de archivo no es hexadecimal")
	}
	if len(raw) == 0 {
		return nil, errors.New("la clave de archivo está vacía")
	}
	key, err := openSealedBox(raw, secretKey)
	if err != nil {
		return nil, fmt.Errorf("no se pudo abrir la clave de archivo: %w", err)
	}
	if len(key) != fileKeySize {
		return nil, errors.New("la clave de archivo debe tener 32 bytes")
	}
	return key, nil
}

func (identity *CryptoIdentity) DecryptFileChunk(key []byte, ivHex, tagHex, dataBase64 string) ([]byte, error) {
	return decryptFileChunk(key, ivHex, tagHex, dataBase64)
}

func decryptFileChunk(key []byte, ivHex, tagHex, dataBase64 string) ([]byte, error) {
	if len(key) != fileKeySize {
		return nil, errors.New("la clave de archivo debe tener 32 bytes")
	}
	iv, err := hex.DecodeString(strings.TrimSpace(ivHex))
	if err != nil || len(iv) != fileChunkIVSize {
		return nil, errors.New("el IV del trozo no es válido")
	}
	tag, err := hex.DecodeString(strings.TrimSpace(tagHex))
	if err != nil || len(tag) != fileChunkTagSize {
		return nil, errors.New("el tag del trozo no es válido")
	}
	data, err := base64.StdEncoding.DecodeString(strings.TrimSpace(dataBase64))
	if err != nil {
		return nil, errors.New("el trozo no está en base64")
	}
	if len(data) == 0 || len(data) > maxFileChunkSize {
		return nil, errors.New("el trozo tiene un tamaño no válido")
	}
	block, err := aes.NewCipher(key)
	if err != nil {
		return nil, fmt.Errorf("no se pudo preparar AES: %w", err)
	}
	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return nil, fmt.Errorf("no se pudo preparar AES-GCM: %w", err)
	}
	sealed := make([]byte, 0, len(data)+len(tag))
	sealed = append(sealed, data...)
	sealed = append(sealed, tag...)
	plaintext, err := gcm.Open(nil, iv, sealed, nil)
	if err != nil {
		return nil, errors.New("el trozo no se pudo descifrar")
	}
	return plaintext, nil
}

func encryptFileChunk(key, plaintext, iv []byte) (data, tag []byte, err error) {
	if len(key) != fileKeySize {
		return nil, nil, errors.New("la clave de archivo debe tener 32 bytes")
	}
	if len(iv) != fileChunkIVSize {
		return nil, nil, errors.New("el IV del trozo no es válido")
	}
	block, err := aes.NewCipher(key)
	if err != nil {
		return nil, nil, fmt.Errorf("no se pudo preparar AES: %w", err)
	}
	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return nil, nil, fmt.Errorf("no se pudo preparar AES-GCM: %w", err)
	}
	sealed := gcm.Seal(nil, iv, plaintext, nil)
	return sealed[:len(sealed)-gcm.Overhead()], sealed[len(sealed)-gcm.Overhead():], nil
}
