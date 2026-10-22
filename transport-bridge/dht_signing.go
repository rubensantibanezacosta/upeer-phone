package transportbridge

import "github.com/upeer/upeer/transport-bridge/dht"

func (identity *CryptoIdentity) SignCanonicalJSON(payloadJSON string) (string, error) {
	return dht.SignCanonicalJSON(identity.secretKey, payloadJSON)
}

func (identity *CryptoIdentity) BuildSignedPacket(senderYggAddress, packetJSON string) (string, error) {
	return dht.BuildSignedPacket(identity.secretKey, senderYggAddress, packetJSON)
}

func BuildSignedPacket(secretKey []byte, senderYggAddress, packetJSON string) (string, error) {
	return dht.BuildSignedPacket(secretKey, senderYggAddress, packetJSON)
}

func VerifyCanonicalJSON(payloadJSON, signatureHex, publicKeyHex string) (bool, error) {
	return dht.VerifyCanonicalJSON(payloadJSON, signatureHex, publicKeyHex)
}

func VerifySignedPacket(packetJSON, publicKeyHex string) (string, error) {
	return dht.VerifySignedPacket(packetJSON, publicKeyHex)
}
