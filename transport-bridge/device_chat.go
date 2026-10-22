package transportbridge

import (
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"net"
	"strconv"
	"strings"
	"time"

	"github.com/upeer/upeer/transport-bridge/dht"
)

func (identity *CryptoIdentity) BuildLinkedChat(
	accountUpeerID string,
	senderYggAddress string,
	peerPublicKeyHex string,
	peerSPKPublicKeyHex string,
	sessionJSON string,
	plaintext string,
	messageID string,
	replyTo string,
	peerSPKID int,
	timestamp int64,
	deviceID string,
) (string, error) {
	if !isYggdrasilAddress(net.ParseIP(senderYggAddress)) {
		return "", errors.New("senderYggAddress must be a Yggdrasil IPv6 address")
	}
	if !validHex(accountUpeerID, 32) || !validHex(deviceID, 32) {
		return "", errors.New("linked chat identity is invalid")
	}
	if !strings.EqualFold(hex.EncodeToString(upeerIDFromPublicKey(identity.publicKey)), deviceID) {
		return "", errors.New("linked chat device id does not match the device key")
	}
	if messageID == "" {
		return "", errors.New("CHAT message ID must not be empty")
	}
	if len(plaintext) > maxOutboundPlainBytes {
		return "", errors.New("CHAT plaintext exceeds the maximum size")
	}
	if timestamp <= 0 {
		timestamp = time.Now().UnixMilli()
	}
	session := strings.TrimSpace(sessionJSON)
	var x3dhInitJSON string
	if session == "" {
		if peerSPKPublicKeyHex == "" || peerSPKID <= 0 {
			return "", errors.New("CHAT needs a signed pre-key to start a session")
		}
		outboundJSON, err := identity.StartOutboundSession(peerPublicKeyHex, peerSPKPublicKeyHex, peerSPKID)
		if err != nil {
			return "", err
		}
		var outbound outboundSessionResult
		if err := json.Unmarshal([]byte(outboundJSON), &outbound); err != nil {
			return "", fmt.Errorf("decode outbound session: %w", err)
		}
		session = outbound.Session
		x3dhInitJSON = outbound.X3DHInit
	}
	encryptedJSON, err := identity.RatchetEncrypt(session, []byte(compressOutboundMessage(plaintext)))
	if err != nil {
		return "", err
	}
	var encrypted ratchetEncryptResult
	if err := json.Unmarshal([]byte(encryptedJSON), &encrypted); err != nil {
		return "", fmt.Errorf("decode encrypted chat: %w", err)
	}
	var message RatchetMessage
	if err := json.Unmarshal([]byte(encrypted.Message), &message); err != nil {
		return "", fmt.Errorf("decode ratchet message: %w", err)
	}
	data := map[string]any{
		"type":           "CHAT",
		"id":             messageID,
		"timestamp":      json.Number(strconv.FormatInt(timestamp, 10)),
		"content":        message.Ciphertext,
		"nonce":          message.Nonce,
		"isInternalSync": false,
		"deviceId":       strings.ToLower(deviceID),
		"senderDeviceId": strings.ToLower(deviceID),
		"ratchetHeader": map[string]any{
			"dh": message.Header.DH,
			"pn": json.Number(strconv.Itoa(message.Header.PN)),
			"n":  json.Number(strconv.Itoa(message.Header.N)),
		},
	}
	if x3dhInitJSON != "" {
		init, err := decodeControlPacket([]byte(x3dhInitJSON))
		if err != nil {
			return "", fmt.Errorf("decode X3DH init: %w", err)
		}
		data["x3dhInit"] = init
	}
	if replyTo != "" {
		data["replyTo"] = replyTo
	}
	dataJSON, err := json.Marshal(data)
	if err != nil {
		return "", fmt.Errorf("encode linked chat data: %w", err)
	}
	signedPacket, err := dht.BuildLinkedSignedPacket(identity.secretKey, accountUpeerID, senderYggAddress, string(dataJSON))
	if err != nil {
		return "", err
	}
	sealed, err := identity.Seal([]byte(signedPacket), peerPublicKeyHex)
	if err != nil {
		return "", err
	}
	outer, err := json.Marshal(map[string]any{"type": "SEALED", "ciphertext": sealed})
	if err != nil {
		return "", fmt.Errorf("encode linked sealed chat: %w", err)
	}
	resultJSON, err := json.Marshal(outboundChatResult{Session: encrypted.Session, Packet: string(outer)})
	if err != nil {
		return "", fmt.Errorf("encode linked outbound chat: %w", err)
	}
	return string(resultJSON), nil
}
