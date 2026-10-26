package transportbridge

import (
	"encoding/base64"
	"encoding/hex"
	"testing"
)

const opensslVectorFileKeyHex = "404142434445464748494a4b4c4d4e4f505152535455565758595a5b5c5d5e5f"

const opensslVectorChunkDataBase64 = "W2EGDRn4K4Cmd6Cyypip2eLkSWwfKMhzi0zI8zLSGzc0H7Zo1A=="

const opensslVectorChunkIVHex = "000102030405060708090a0b"

const opensslVectorChunkTagHex = "d9d259effa2c4c35abaa9ce005f04cb2"

const opensslVectorChunkPlainBase64 = "YWRqdW50byBkZSBwcnVlYmEgZGVzZGUgZWwgZXNjcml0b3Jpbw=="

func TestDecryptFileChunkAbreElVectorDeOpenSSL(t *testing.T) {
	key, err := hex.DecodeString(opensslVectorFileKeyHex)
	if err != nil {
		t.Fatalf("la clave del vector no es hexadecimal: %v", err)
	}
	opened, err := decryptFileChunk(key, opensslVectorChunkIVHex, opensslVectorChunkTagHex, opensslVectorChunkDataBase64)
	if err != nil {
		t.Fatalf("no se pudo descifrar el vector de OpenSSL: %v", err)
	}
	expected, err := base64.StdEncoding.DecodeString(opensslVectorChunkPlainBase64)
	if err != nil {
		t.Fatalf("el texto plano del vector no es base64: %v", err)
	}
	if string(opened) != string(expected) {
		t.Fatalf("texto plano distinto: %q", opened)
	}
}

func TestDecryptFileChunkRechazaElVectorConTagAlterado(t *testing.T) {
	key, err := hex.DecodeString(opensslVectorFileKeyHex)
	if err != nil {
		t.Fatalf("la clave del vector no es hexadecimal: %v", err)
	}
	tag, err := hex.DecodeString(opensslVectorChunkTagHex)
	if err != nil {
		t.Fatalf("el tag del vector no es hexadecimal: %v", err)
	}
	tag[0] ^= 0xff
	if _, err := decryptFileChunk(key, opensslVectorChunkIVHex, hex.EncodeToString(tag), opensslVectorChunkDataBase64); err == nil {
		t.Fatal("un tag alterado no debería descifrar")
	}
}
