package transportbridge

import "github.com/upeer/upeer/transport-bridge/dht"

type signedPacketMetadata = dht.SignedPacketMetadata

func (b *Bridge) ValidateSignedPacket(payload []byte, publicKeyHex string) (string, error) {
	return dht.VerifySignedPacket(string(payload), publicKeyHex)
}
