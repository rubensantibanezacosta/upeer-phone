package transportbridge

import (
	"bytes"
	"compress/gzip"
	"crypto/ed25519"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"net"
	"strconv"
	"strings"
	"time"
	"unicode/utf16"

	"github.com/upeer/upeer/transport-bridge/dht"
)

const (
	compressionPrefix     = "\u2763UP\u2763"
	compressionThreshold  = 512
	directRatchetContext  = "direct"
	maxOutboundPlainBytes = 9 * 1024 * 1024
)

type outboundChatResult struct {
	Session string `json:"session"`
	Packet  string `json:"packet"`
}

func (identity *CryptoIdentity) BuildChat(
	senderYggAddress string,
	peerPublicKeyHex string,
	peerSPKPublicKeyHex string,
	sessionJSON string,
	plaintext string,
	messageID string,
	replyTo string,
	peerSPKID int,
	timestamp int64,
) (string, error) {
	return identity.buildChatPacket(
		"CHAT",
		"id",
		messageID,
		replyTo,
		0,
		senderYggAddress,
		peerPublicKeyHex,
		peerSPKPublicKeyHex,
		sessionJSON,
		plaintext,
		peerSPKID,
		timestamp,
	)
}

func (identity *CryptoIdentity) BuildChatUpdate(
	senderYggAddress string,
	peerPublicKeyHex string,
	peerSPKPublicKeyHex string,
	sessionJSON string,
	plaintext string,
	messageID string,
	version int,
	peerSPKID int,
	timestamp int64,
) (string, error) {
	if version <= 0 {
		return "", errors.New("CHAT_UPDATE version must be positive")
	}
	return identity.buildChatPacket(
		"CHAT_UPDATE",
		"msgId",
		messageID,
		"",
		version,
		senderYggAddress,
		peerPublicKeyHex,
		peerSPKPublicKeyHex,
		sessionJSON,
		plaintext,
		peerSPKID,
		timestamp,
	)
}

func (identity *CryptoIdentity) buildChatPacket(
	packetType string,
	idField string,
	messageID string,
	replyTo string,
	version int,
	senderYggAddress string,
	peerPublicKeyHex string,
	peerSPKPublicKeyHex string,
	sessionJSON string,
	plaintext string,
	peerSPKID int,
	timestamp int64,
) (string, error) {
	data, encrypted, err := identity.buildChatData(
		packetType,
		idField,
		messageID,
		replyTo,
		version,
		senderYggAddress,
		peerPublicKeyHex,
		peerSPKPublicKeyHex,
		sessionJSON,
		plaintext,
		peerSPKID,
		timestamp,
	)
	if err != nil {
		return "", err
	}
	signedPacket, err := identity.buildSignedPacket(data, senderYggAddress)
	if err != nil {
		return "", err
	}
	return identity.sealOutboundChat(signedPacket, peerPublicKeyHex, encrypted.Session)
}

func (identity *CryptoIdentity) buildChatData(
	packetType string,
	idField string,
	messageID string,
	replyTo string,
	version int,
	senderYggAddress string,
	peerPublicKeyHex string,
	peerSPKPublicKeyHex string,
	sessionJSON string,
	plaintext string,
	peerSPKID int,
	timestamp int64,
) (map[string]any, ratchetEncryptResult, error) {
	var encrypted ratchetEncryptResult
	if !isYggdrasilAddress(net.ParseIP(senderYggAddress)) {
		return nil, encrypted, errors.New("senderYggAddress must be a Yggdrasil IPv6 address")
	}
	if messageID == "" {
		return nil, encrypted, fmt.Errorf("%s message ID must not be empty", packetType)
	}
	if len(plaintext) > maxOutboundPlainBytes {
		return nil, encrypted, fmt.Errorf("%s plaintext exceeds the maximum size", packetType)
	}
	if timestamp <= 0 {
		timestamp = time.Now().UnixMilli()
	}
	session := strings.TrimSpace(sessionJSON)
	var x3dhInitJSON string
	if session == "" {
		if peerSPKPublicKeyHex == "" || peerSPKID <= 0 {
			return nil, encrypted, fmt.Errorf("%s needs a signed pre-key to start a session", packetType)
		}
		outboundJSON, err := identity.StartOutboundSession(peerPublicKeyHex, peerSPKPublicKeyHex, peerSPKID)
		if err != nil {
			return nil, encrypted, err
		}
		var outbound outboundSessionResult
		if err := json.Unmarshal([]byte(outboundJSON), &outbound); err != nil {
			return nil, encrypted, fmt.Errorf("decode outbound session: %w", err)
		}
		session = outbound.Session
		x3dhInitJSON = outbound.X3DHInit
	}
	encryptedJSON, err := identity.RatchetEncrypt(session, []byte(compressOutboundMessage(plaintext)))
	if err != nil {
		return nil, encrypted, err
	}
	if err := json.Unmarshal([]byte(encryptedJSON), &encrypted); err != nil {
		return nil, encrypted, fmt.Errorf("decode encrypted chat: %w", err)
	}
	var message RatchetMessage
	if err := json.Unmarshal([]byte(encrypted.Message), &message); err != nil {
		return nil, encrypted, fmt.Errorf("decode ratchet message: %w", err)
	}
	data := map[string]any{
		"type":           packetType,
		idField:          messageID,
		"timestamp":      json.Number(strconv.FormatInt(timestamp, 10)),
		"content":        message.Ciphertext,
		"nonce":          message.Nonce,
		"isInternalSync": false,
		"ratchetHeader": map[string]any{
			"dh": message.Header.DH,
			"pn": json.Number(strconv.Itoa(message.Header.PN)),
			"n":  json.Number(strconv.Itoa(message.Header.N)),
		},
	}
	if version > 0 {
		data["version"] = json.Number(strconv.Itoa(version))
	}
	if x3dhInitJSON != "" {
		init, err := decodeControlPacket([]byte(x3dhInitJSON))
		if err != nil {
			return nil, encrypted, fmt.Errorf("decode X3DH init: %w", err)
		}
		data["x3dhInit"] = init
	}
	if replyTo != "" {
		data["replyTo"] = replyTo
	}
	return data, encrypted, nil
}

func (identity *CryptoIdentity) sealOutboundChat(signedPacket, peerPublicKeyHex, session string) (string, error) {
	sealed, err := identity.Seal([]byte(signedPacket), peerPublicKeyHex)
	if err != nil {
		return "", err
	}
	outer, err := json.Marshal(map[string]any{"type": "SEALED", "ciphertext": sealed})
	if err != nil {
		return "", fmt.Errorf("encode sealed chat: %w", err)
	}
	resultJSON, err := json.Marshal(outboundChatResult{Session: session, Packet: string(outer)})
	if err != nil {
		return "", fmt.Errorf("encode outbound chat: %w", err)
	}
	return string(resultJSON), nil
}

func (identity *CryptoIdentity) BuildHandshakeRequest(senderYggAddress, signedPreKeyJSON, alias string) (string, error) {
	return identity.buildHandshakeRequest(senderYggAddress, signedPreKeyJSON, alias, nil)
}

func (identity *CryptoIdentity) BuildDrReset(senderYggAddress, signedPreKeyJSON, ratchetContext string) (string, error) {
	if !isYggdrasilAddress(net.ParseIP(senderYggAddress)) {
		return "", errors.New("senderYggAddress must be a Yggdrasil IPv6 address")
	}
	data, err := identity.buildDrResetData(signedPreKeyJSON, ratchetContext)
	if err != nil {
		return "", err
	}
	return identity.buildSignedPacket(data, senderYggAddress)
}

func (identity *CryptoIdentity) buildDrResetData(signedPreKeyJSON, ratchetContext string) (map[string]any, error) {
	signedPreKey, err := identity.validatedSignedPreKey(signedPreKeyJSON)
	if err != nil {
		return nil, err
	}
	if ratchetContext == "" {
		ratchetContext = directRatchetContext
	}
	if !validRatchetContext(ratchetContext) {
		return nil, errors.New("ratchetContext must be direct or group:<id>")
	}
	resetID, err := newResetID()
	if err != nil {
		return nil, err
	}
	return map[string]any{
		"type":    "DR_RESET",
		"resetId": resetID,
		"signedPreKey": map[string]any{
			"spkPub": signedPreKey.SPKPub,
			"spkSig": signedPreKey.SPKSig,
			"spkId":  json.Number(strconv.Itoa(signedPreKey.SPKID)),
		},
		"ratchetContext": ratchetContext,
		"isInternalSync": false,
	}, nil
}

func (identity *CryptoIdentity) VerifySignedPreKey(peerPublicKeyHex, spkPubHex, spkSigHex string) bool {
	publicKey, err := hex.DecodeString(peerPublicKeyHex)
	if err != nil || len(publicKey) != ed25519.PublicKeySize {
		return false
	}
	spkPublicKey, err := hex.DecodeString(spkPubHex)
	if err != nil || len(spkPublicKey) != ed25519.PublicKeySize {
		return false
	}
	signature, err := hex.DecodeString(spkSigHex)
	if err != nil || len(signature) != ed25519.SignatureSize {
		return false
	}
	return ed25519.Verify(publicKey, spkPublicKey, signature)
}

func (identity *CryptoIdentity) buildSignedPacket(data map[string]any, senderYggAddress string) (string, error) {
	return dht.BuildSignedPacketFromObject(identity.secretKey, senderYggAddress, data)
}

func (identity *CryptoIdentity) validatedSignedPreKey(signedPreKeyJSON string) (signedPreKeyState, error) {
	var signedPreKey signedPreKeyState
	if err := json.Unmarshal([]byte(signedPreKeyJSON), &signedPreKey); err != nil {
		return signedPreKey, fmt.Errorf("decode signed pre-key: %w", err)
	}
	if signedPreKey.SPKID <= 0 || !validHex(signedPreKey.SPKPub, 32) || !validHex(signedPreKey.SPKSig, 64) {
		return signedPreKey, errors.New("signed pre-key state is invalid")
	}
	spkPublicKey, err := hex.DecodeString(signedPreKey.SPKPub)
	if err != nil {
		return signedPreKey, fmt.Errorf("decode signed pre-key public key: %w", err)
	}
	signature, err := hex.DecodeString(signedPreKey.SPKSig)
	if err != nil {
		return signedPreKey, fmt.Errorf("decode signed pre-key signature: %w", err)
	}
	if !ed25519.Verify(identity.publicKey, spkPublicKey, signature) {
		return signedPreKey, errors.New("signed pre-key signature does not match identity")
	}
	return signedPreKey, nil
}

func validRatchetContext(value string) bool {
	if value == directRatchetContext {
		return true
	}
	if !strings.HasPrefix(value, "group:") {
		return false
	}
	groupID := strings.TrimPrefix(value, "group:")
	return groupID != "" && !strings.Contains(groupID, ":")
}

func compressOutboundMessage(value string) string {
	if utf16Length(value) < compressionThreshold {
		return value
	}
	var compressed bytes.Buffer
	writer := gzip.NewWriter(&compressed)
	if _, err := writer.Write([]byte(value)); err != nil {
		return value
	}
	if err := writer.Close(); err != nil {
		return value
	}
	wrapped := compressionPrefix + base64.StdEncoding.EncodeToString(compressed.Bytes())
	if utf16Length(wrapped) >= utf16Length(value) {
		return value
	}
	return wrapped
}

func utf16Length(value string) int {
	return len(utf16.Encode([]rune(value)))
}
