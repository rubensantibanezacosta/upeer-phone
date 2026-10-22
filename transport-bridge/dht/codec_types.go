package dht

const (
	MaxPacketJSONBytes      = 64 * 1024
	MaxPacketJSONDepth      = 16
	MaxQueryIDLength        = 64
	MaxNodeIDLength         = 128
	MinUpeerIDLength        = 32
	MaxUpeerIDLength        = 128
	MaxAddressLength        = 100
	MaxResponseNodes        = 20
	MaxResponseNeighbors    = 20
	MaxExchangePeers        = 50
	MaxStoredValueBytes     = 10_000
	MaxStoreTTLSeconds      = 2_592_000
	MaxPowProofLength       = 256
	MaxPendingQueryAgeNanos = 30_000_000_000
)

type DecodedPacket struct {
	Type    string
	QueryID string
	Message any
}

type DhtNode struct {
	UpeerID   string
	Address   string
	PublicKey string
	NodeID    string
}

type DhtNeighbor struct {
	UpeerID       string
	Address       string
	PublicKey     string
	LocationBlock map[string]any
}

type DhtExchangePeer struct {
	UpeerID       string
	PublicKey     string
	LocationBlock map[string]any
}

type DhtPing struct {
	QueryID string
	NodeID  string
}

type DhtPong struct {
	QueryID string
	NodeID  string
}

type DhtFindNode struct {
	QueryID  string
	TargetID string
}

type DhtFoundNodes struct {
	QueryID string
	Nodes   []DhtNode
}

type DhtFindValue struct {
	QueryID string
	Key     string
}

type DhtFoundValue struct {
	QueryID   string
	Key       string
	Value     any
	Nodes     []DhtNode
	Publisher string
	Timestamp *int64
}

type DhtStore struct {
	QueryID   string
	Key       string
	Value     any
	Publisher string
	Timestamp *int64
	TTL       *int64
}

type DhtStoreAck struct {
	QueryID string
	Key     string
}

type DhtQuery struct {
	QueryID         string
	TargetID        string
	ReferralContext map[string]any
}

type DhtResponse struct {
	QueryID       string
	TargetID      string
	PublicKey     string
	LocationBlock map[string]any
	Neighbors     []DhtNeighbor
}

type DhtUpdate struct {
	QueryID       string
	LocationBlock map[string]any
}

type DhtExchange struct {
	QueryID string
	Peers   []DhtExchangePeer
}
