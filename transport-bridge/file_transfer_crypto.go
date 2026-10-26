package transportbridge

import (
	"crypto/aes"
	"crypto/cipher"
	"crypto/ed25519"
	"crypto/rand"
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

func newFileKey() ([]byte, error) {
	key := make([]byte, fileKeySize)
	if _, err := rand.Read(key); err != nil {
		return nil, fmt.Errorf("no se pudo generar la clave de archivo: %w", err)
	}
	return key, nil
}

func (identity *CryptoIdentity) NewFileKey() ([]byte, error) {
	if identity == nil {
		return nil, errors.New("no hay identidad para generar la clave de archivo")
	}
	return newFileKey()
}

func (identity *CryptoIdentity) SealFileKeyFor(fileKey []byte, recipientPublicKeyHex string) (string, error) {
	if identity == nil {
		return "", errors.New("no hay identidad para sellar la clave de archivo")
	}
	if len(fileKey) != fileKeySize {
		return "", errors.New("la clave de archivo debe tener 32 bytes")
	}
	recipientPublicKey, err := hex.DecodeString(strings.TrimSpace(recipientPublicKeyHex))
	if err != nil {
		return "", errors.New("la clave pública del destinatario no es hexadecimal")
	}
	if len(recipientPublicKey) != ed25519.PublicKeySize {
		return "", errors.New("la clave pública del destinatario debe tener 32 bytes")
	}
	sealed, err := sealBox(fileKey, recipientPublicKey)
	if err != nil {
		return "", fmt.Errorf("no se pudo sellar la clave de archivo: %w", err)
	}
	return hex.EncodeToString(sealed), nil
}

// FileChunkCiphertext es el trozo cifrado tal y como viaja en un FILE_CHUNK.
type FileChunkCiphertext struct {
	Data string
	IV   string
	Tag  string
}

func (identity *CryptoIdentity) EncryptFileChunk(key, plaintext []byte) (*FileChunkCiphertext, error) {
	if identity == nil {
		return nil, errors.New("no hay identidad para cifrar el trozo")
	}
	if len(plaintext) == 0 || len(plaintext) > maxFileChunkSize {
		return nil, errors.New("el trozo tiene un tamaño no válido")
	}
	iv := make([]byte, fileChunkIVSize)
	if _, err := rand.Read(iv); err != nil {
		return nil, fmt.Errorf("no se pudo generar el IV del trozo: %w", err)
	}
	data, tag, err := encryptFileChunk(key, plaintext, iv)
	if err != nil {
		return nil, err
	}
	return &FileChunkCiphertext{
		Data: base64.StdEncoding.EncodeToString(data),
		IV:   hex.EncodeToString(iv),
		Tag:  hex.EncodeToString(tag),
	}, nil
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
