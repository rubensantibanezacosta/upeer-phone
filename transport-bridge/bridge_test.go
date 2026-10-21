package transportbridge

import (
	"crypto/ed25519"
	"encoding/hex"
	"encoding/json"
	"io"
	"log"
	"net"
	"net/url"
	"os"
	"testing"
	"time"

	"github.com/yggdrasil-network/yggdrasil-go/src/config"
	"github.com/yggdrasil-network/yggdrasil-go/src/core"
	"golang.org/x/crypto/blake2b"
)

func TestConfigWithPeersUsesIndependentNodeSeed(t *testing.T) {
	seed := make([]byte, ed25519.SeedSize)
	for index := range seed {
		seed[index] = byte(index)
	}
	nodeSeed := append([]byte(nil), seed...)
	configJSON, err := NewBridge().NodeConfigWithPeers(`["tls://one.example:443","tcp://two.example:1234"]`, nodeSeed)
	if err != nil {
		t.Fatal(err)
	}
	var cfg config.NodeConfig
	if err := cfg.UnmarshalHJSON([]byte(configJSON)); err != nil {
		t.Fatal(err)
	}
	expectedKey := ed25519.NewKeyFromSeed(nodeSeed)
	if !ed25519.PrivateKey(cfg.PrivateKey).Equal(expectedKey) {
		t.Fatal("Yggdrasil private key does not match the independent node seed")
	}
	if !ed25519.PrivateKey(cfg.Certificate.PrivateKey.(ed25519.PrivateKey)).Equal(expectedKey) {
		t.Fatal("Yggdrasil certificate does not match the independent node seed")
	}
	if ed25519.PrivateKey(cfg.PrivateKey).Equal(ed25519.NewKeyFromSeed(seed)) == false {
		t.Fatal("test node key unexpectedly matches the uPeer seed")
	}
}

func TestNodeConfigRejectsInvalidPeerLists(t *testing.T) {
	seed := make([]byte, ed25519.SeedSize)
	for _, peers := range []string{`["tls://one.example:443"]`, `["tls://one.example:443","tls://one.example:443"]`, `["https://one.example:443","tcp://two.example:443"]`} {
		if _, err := NewBridge().NodeConfigWithPeers(peers, seed); err == nil {
			t.Fatalf("node config accepted invalid peer list %s", peers)
		}
	}
}

func TestStartWithPeersStartsBridgeUsingSuppliedSeed(t *testing.T) {
	seed := make([]byte, ed25519.SeedSize)
	for index := range seed {
		seed[index] = byte(index + 1)
	}
	bridge := NewBridge()
	peers := `["tls://one.example:443","tcp://two.example:1234"]`
	if err := bridge.StartWithPeers(peers, seed); err != nil {
		t.Fatalf("start bridge with peers: %v", err)
	}
	defer bridge.Stop()
	if _, err := bridge.Address(); err != nil {
		t.Fatalf("read started node address: %v", err)
	}
	if err := bridge.StartWithPeers(peers, seed); err == nil {
		t.Fatal("StartWithPeers accepted an already started bridge")
	}
}

func TestBridgeNodeConfigWithPeers(t *testing.T) {
	seed, err := GenerateNodeSeed()
	if err != nil {
		t.Fatal(err)
	}
	configJSON, err := NewBridge().NodeConfigWithPeers(`["tls://one.example:443","tcp://two.example:1234"]`, seed)
	if err != nil {
		t.Fatal(err)
	}
	var cfg config.NodeConfig
	if err := cfg.UnmarshalHJSON([]byte(configJSON)); err != nil {
		t.Fatal(err)
	}
	if len(cfg.PrivateKey) != ed25519.PrivateKeySize {
		t.Fatalf("generated private key length = %d", len(cfg.PrivateKey))
	}
	seededKey := ed25519.NewKeyFromSeed(seed)
	if !ed25519.PrivateKey(cfg.PrivateKey).Equal(seededKey) {
		t.Fatal("bridge node configuration does not use its generated independent seed")
	}
}

func TestNodeConfigWithPeersAllowsBridgeManagedDiscovery(t *testing.T) {
	seed := make([]byte, ed25519.SeedSize)
	configJSON, err := NewBridge().NodeConfigWithPeers(`[]`, seed)
	if err != nil {
		t.Fatalf("build empty-peer node config: %v", err)
	}
	var cfg config.NodeConfig
	if err := cfg.UnmarshalHJSON([]byte(configJSON)); err != nil {
		t.Fatalf("parse empty-peer node config: %v", err)
	}
	if len(cfg.Peers) != 0 {
		t.Fatalf("node config peers = %v, want service-managed peer discovery", cfg.Peers)
	}
}

func TestGenerateNodeSeedReturnsIndependentRandomSeeds(t *testing.T) {
	first, err := GenerateNodeSeed()
	if err != nil {
		t.Fatal(err)
	}
	second, err := GenerateNodeSeed()
	if err != nil {
		t.Fatal(err)
	}
	if len(first) != ed25519.SeedSize || len(second) != ed25519.SeedSize {
		t.Fatalf("generated seed lengths = %d and %d", len(first), len(second))
	}
	if string(first) == string(second) {
		t.Fatal("independent node seeds unexpectedly match")
	}
}

func TestDeriveIdentityMatchesDesktopBIP39Vector(t *testing.T) {
	seed, err := hex.DecodeString("5eb00bbddcf069084889a8ab9155568165f5c453ccb85e70811aaed6f6da5fc1")
	if err != nil {
		t.Fatal(err)
	}
	derivedJSON, err := NewBridge().DeriveIdentity(seed)
	if err != nil {
		t.Fatal(err)
	}
	var derived struct {
		PublicKey string `json:"publicKey"`
		SecretKey string `json:"secretKey"`
		UPeerID   string `json:"upeerId"`
	}
	if err := json.Unmarshal([]byte(derivedJSON), &derived); err != nil {
		t.Fatal(err)
	}
	expectedKey := ed25519.NewKeyFromSeed(seed)
	expectedPublicKey := expectedKey.Public().(ed25519.PublicKey)
	if derived.PublicKey != hex.EncodeToString(expectedPublicKey) || derived.SecretKey != hex.EncodeToString(expectedKey) {
		t.Fatal("derived identity keys differ from Desktop Ed25519 seed derivation")
	}
	hash, err := blake2b.New(32, nil)
	if err != nil {
		t.Fatal(err)
	}
	_, _ = hash.Write(expectedPublicKey)
	if derived.UPeerID != hex.EncodeToString(hash.Sum(nil)) {
		t.Fatal("derived uPeer ID differs from Desktop BLAKE2b-256 identity hash")
	}
}

func TestBridgeReadsTCPInChunks(t *testing.T) {
	bridge := NewBridge()
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer listener.Close()
	go func() {
		conn, err := listener.Accept()
		if err != nil {
			return
		}
		defer conn.Close()
		_, _ = conn.Write([]byte("abc"))
		time.Sleep(50 * time.Millisecond)
		_, _ = conn.Write([]byte("def"))
	}()
	bridge.connections[1] = dialTestConnection(t, listener.Addr().String())
	bridge.node = &core.Core{}
	first, err := bridge.Read(1, 3, 1000)
	if err != nil || string(first) != "abc" {
		t.Fatalf("first TCP chunk = %q, %v", first, err)
	}
	second, err := bridge.Read(1, 3, 1000)
	if err != nil || string(second) != "def" {
		t.Fatalf("second TCP chunk = %q, %v", second, err)
	}
}

func TestBridgeLifecycleAndEndpointValidation(t *testing.T) {
	bridge := NewBridge()
	if _, err := bridge.Address(); err == nil {
		t.Fatal("Address succeeded before Start")
	}
	if _, err := bridge.DialTCP("200::1", 50005); err == nil {
		t.Fatal("DialTCP succeeded before Start")
	}
	for _, endpoint := range []struct {
		address string
		port    int
	}{{"127.0.0.1", 50005}, {"200::1", 0}, {"200::1", 65536}} {
		if err := validateEndpoint(endpoint.address, endpoint.port); err == nil {
			t.Fatalf("endpoint validation accepted %s:%d", endpoint.address, endpoint.port)
		}
	}
	if err := bridge.StartAutoconfigure(); err != nil {
		t.Fatalf("start local node: %v", err)
	}
	if err := bridge.StartAutoconfigure(); err == nil {
		t.Fatal("second Start succeeded")
	}
	if _, err := bridge.Address(); err != nil {
		t.Fatalf("get local address: %v", err)
	}
	if err := bridge.Stop(); err != nil {
		t.Fatalf("stop local node: %v", err)
	}
	if err := bridge.Stop(); err != nil {
		t.Fatalf("second Stop: %v", err)
	}
}

func TestPeerStatusReportsConfiguredAndInboundState(t *testing.T) {
	bridge := NewBridge()
	if err := bridge.StartAutoconfigure(); err != nil {
		t.Fatalf("start node for peer status: %v", err)
	}
	defer bridge.Stop()
	status, err := bridge.PeerStatusJSON()
	if err != nil {
		t.Fatalf("get peer status: %v", err)
	}
	var peers []map[string]any
	if err := json.Unmarshal([]byte(status), &peers); err != nil {
		t.Fatalf("decode peer status: %v", err)
	}
	if peers == nil {
		t.Fatal("peer status must serialize an empty list rather than null")
	}
}

func TestPeerStatusRequiresRunningBridge(t *testing.T) {
	bridge := NewBridge()
	if _, err := bridge.PeerStatusJSON(); err == nil {
		t.Fatal("peer status succeeded before Start")
	}
}

func TestProbePeerUsesTCPReachability(t *testing.T) {
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer listener.Close()
	go func() {
		conn, err := listener.Accept()
		if err == nil {
			conn.Close()
		}
	}()
	latency, err := ProbePeer("tcp://" + listener.Addr().String())
	if err != nil || latency < 0 {
		t.Fatalf("probe local TCP peer = %dms, %v", latency, err)
	}
}

func TestReplacePeersValidatesBeforeChangingNode(t *testing.T) {
	if _, err := parseReplacementPeers(`["https://peer.example:443"]`); err == nil {
		t.Fatal("replacement accepted a non-Yggdrasil peer scheme")
	}
	if _, err := parseReplacementPeers(`["tls://peer.example"]`); err == nil {
		t.Fatal("replacement accepted a peer without a port")
	}
	if _, err := parseReplacementPeers(`null`); err == nil {
		t.Fatal("replacement accepted a null peer list")
	}
}

func TestReplacementPeerListRequiresTwoOrThreeUniquePeers(t *testing.T) {
	for _, peers := range []string{
		`["tcp://one.example:1234"]`,
		`["tcp://one.example:1234","tcp://one.example:1234"]`,
		`["tcp://one.example:1234","tcp://two.example:1234","tcp://three.example:1234","tcp://four.example:1234"]`,
	} {
		if _, err := parseReplacementPeers(peers); err == nil {
			t.Fatalf("replacement peer list accepted %s", peers)
		}
	}
}

func TestReplaceConfiguredPeersKeepsAndRetriesAlreadyConfiguredPeer(t *testing.T) {
	bridge := NewBridge()
	if err := bridge.StartAutoconfigure(); err != nil {
		t.Fatalf("start node for replacement rollback: %v", err)
	}
	defer bridge.Stop()
	node := bridge.node
	original := []*url.URL{mustPeerURL(t, "tcp://127.0.0.1:1")}
	if err := node.AddPeer(original[0], ""); err != nil {
		t.Fatalf("add original peer: %v", err)
	}
	replacement := mustPeerURL(t, "tcp://127.0.0.1:2")
	if err := replaceConfiguredPeers(node, []*url.URL{replacement, mustPeerURL(t, "tcp://127.0.0.1:1")}); err != nil {
		t.Fatalf("replace peers including already configured peer: %v", err)
	}
	peers := node.GetPeers()
	if len(peers) != 2 {
		t.Fatalf("configured peers after replacement = %#v", peers)
	}
}

func TestReplaceConfiguredPeersKeepsOldPeerUntilItIsRetired(t *testing.T) {
	log.SetOutput(io.Discard)
	t.Cleanup(func() { log.SetOutput(os.Stderr) })
	bridge := NewBridge()
	if err := bridge.StartAutoconfigure(); err != nil {
		t.Fatalf("start node for inbound preservation: %v", err)
	}
	defer bridge.Stop()
	peer := mustPeerURL(t, "tcp://127.0.0.1:1")
	if err := bridge.node.AddPeer(peer, ""); err != nil {
		t.Fatalf("add initial outbound peer: %v", err)
	}
	deadline := time.Now().Add(time.Second)
	for len(bridge.node.GetPeers()) == 0 && time.Now().Before(deadline) {
		time.Sleep(time.Millisecond)
	}
	replacement := mustPeerURL(t, "tcp://127.0.0.1:2")
	if err := replaceConfiguredPeers(bridge.node, []*url.URL{replacement}); err != nil {
		t.Fatalf("replace configured peers: %v", err)
	}
	deadline = time.Now().Add(time.Second)
	for time.Now().Before(deadline) {
		peers := bridge.node.GetPeers()
		if len(peers) == 1 && peers[0].URI == replacement.String() {
			return
		}
		time.Sleep(time.Millisecond)
	}
	t.Fatalf("configured peers after old peer retirement = %#v", bridge.node.GetPeers())
}

func mustPeerURL(t *testing.T, value string) *url.URL {
	t.Helper()
	peer, err := url.Parse(value)
	if err != nil {
		t.Fatalf("parse test peer URI: %v", err)
	}
	return peer
}

func TestProbePeerRejectsInvalidEndpoints(t *testing.T) {
	for _, uri := range []string{"", "https://peer.example:443", "tls://peer.example", "tcp://peer.example:70000"} {
		if _, err := ProbePeer(uri); err == nil {
			t.Fatalf("probe accepted invalid URI %q", uri)
		}
	}
}

func TestProbePeerLatencyReturnsFailureSentinel(t *testing.T) {
	if latency := ProbePeerLatency(""); latency != -1 {
		t.Fatalf("invalid peer latency = %d, want -1", latency)
	}
}

func TestValidateCallMediaDatagramSize(t *testing.T) {
	bridge := NewBridge()
	if err := bridge.WriteCallMediaDatagram(1, nil); err == nil {
		t.Fatal("empty datagram was accepted")
	}
	if err := bridge.WriteCallMediaDatagram(1, make([]byte, 1201)); err == nil {
		t.Fatal("oversized datagram was accepted")
	}
}

func dialTestConnection(t *testing.T, address string) net.Conn {
	t.Helper()
	conn, err := net.Dial("tcp", address)
	if err != nil {
		t.Fatalf("dial TCP test connection: %v", err)
	}
	return conn
}
