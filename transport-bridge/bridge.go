package transportbridge

import (
	"context"
	"crypto/ed25519"
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"net"
	"net/url"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/yggdrasil-network/yggdrasil-go/src/config"
	"github.com/yggdrasil-network/yggdrasil-go/src/core"
	"github.com/yggdrasil-network/yggstack/src/netstack"
	"golang.org/x/crypto/blake2b"
	"gvisor.dev/gvisor/pkg/tcpip/adapters/gonet"
)

type Bridge struct {
	mutex              sync.Mutex
	lifecycle          sync.Mutex
	node               *core.Core
	stack              *netstack.YggdrasilNetstack
	running            bool
	connections        map[int32]net.Conn
	listeners          map[int32]*listenerState
	peerManager        *PeerManager
	peerManagerEnabled bool
}

func (b *Bridge) DeriveIdentity(seed []byte) (string, error) {
	if len(seed) != ed25519.SeedSize {
		return "", errors.New("identity seed must be 32 bytes")
	}
	privateKey := ed25519.NewKeyFromSeed(seed)
	publicKey := privateKey.Public().(ed25519.PublicKey)
	upeerID, err := blake2b.New(32, nil)
	if err != nil {
		return "", fmt.Errorf("create uPeer identity hash: %w", err)
	}
	if _, err := upeerID.Write(publicKey); err != nil {
		return "", fmt.Errorf("hash uPeer public key: %w", err)
	}
	return fmt.Sprintf("{\"publicKey\":\"%x\",\"secretKey\":\"%x\",\"upeerId\":\"%x\"}", publicKey, privateKey, upeerID.Sum(nil)), nil
}

func NewBridge() *Bridge {
	return &Bridge{
		connections: make(map[int32]net.Conn),
		listeners:   make(map[int32]*listenerState),
	}
}

func (b *Bridge) NodeConfigWithPeers(peersJSON string, seed []byte) (string, error) {
	var peers []string
	if err := json.Unmarshal([]byte(peersJSON), &peers); err != nil {
		return "", fmt.Errorf("parse Yggdrasil peer list: %w", err)
	}
	if len(seed) != ed25519.SeedSize {
		return "", errors.New("Yggdrasil node seed must be 32 bytes")
	}
	if len(peers) == 0 {
		return b.nodeConfigWithPeerURIs(nil, seed)
	}
	validatedPeers, err := parsePeerList(peers)
	if err != nil {
		return "", err
	}
	return b.nodeConfigWithPeerURIs(validatedPeers, seed)
}

func (b *Bridge) nodeConfigWithPeerURIs(peers []string, seed []byte) (string, error) {
	if len(seed) != ed25519.SeedSize {
		return "", errors.New("Yggdrasil node seed must be 32 bytes")
	}
	cfg := config.GenerateConfig()
	privateKey := ed25519.NewKeyFromSeed(seed)
	cfg.PrivateKey = config.KeyBytes(privateKey)
	if err := cfg.GenerateSelfSignedCertificate(); err != nil {
		return "", fmt.Errorf("generate Yggdrasil node certificate: %w", err)
	}
	cfg.IfName = "none"
	cfg.MulticastInterfaces = []config.MulticastInterfaceConfig{}
	cfg.NodeInfoPrivacy = true
	cfg.NodeInfo = map[string]interface{}{"client": "uPeer Android"}
	cfg.Peers = peers
	encoded, err := json.Marshal(cfg)
	if err != nil {
		return "", fmt.Errorf("encode Yggdrasil configuration: %w", err)
	}
	return string(encoded), nil
}

func parsePeerList(peers []string) ([]string, error) {
	if len(peers) < 2 || len(peers) > 3 {
		return nil, errors.New("Yggdrasil peer list must contain between two and three peers")
	}
	seen := make(map[string]struct{}, len(peers))
	validated := make([]string, 0, len(peers))
	for _, peerURI := range peers {
		parsed, err := url.Parse(peerURI)
		if err != nil || (parsed.Scheme != "tcp" && parsed.Scheme != "tls") || parsed.Host == "" {
			return nil, fmt.Errorf("invalid Yggdrasil peer URI %q", peerURI)
		}
		port, err := strconv.Atoi(parsed.Port())
		if err != nil || port < 1 || port > 65535 {
			return nil, fmt.Errorf("invalid Yggdrasil peer port in %q", peerURI)
		}
		key := normalizedPeerURI(parsed)
		if _, exists := seen[key]; exists {
			return nil, fmt.Errorf("duplicate Yggdrasil peer URI %q", peerURI)
		}
		seen[key] = struct{}{}
		validated = append(validated, key)
	}
	return validated, nil
}

func normalizedPeerURI(peer *url.URL) string {
	normalized := *peer
	normalized.RawQuery = ""
	normalized.Host = strings.ToLower(normalized.Host)
	return normalized.String()
}

func (b *Bridge) StartWithPeers(peersJSON string, seed []byte) error {
	configJSON, err := b.NodeConfigWithPeers(peersJSON, seed)
	if err != nil {
		return err
	}
	return b.Start([]byte(configJSON))
}

func GenerateNodeSeed() ([]byte, error) {
	seed := make([]byte, ed25519.SeedSize)
	if _, err := rand.Read(seed); err != nil {
		return nil, fmt.Errorf("generate Yggdrasil node seed: %w", err)
	}
	return seed, nil
}

func (b *Bridge) Start(configJSON []byte) error {
	b.lifecycle.Lock()
	defer b.lifecycle.Unlock()
	b.mutex.Lock()
	if b.node != nil {
		b.mutex.Unlock()
		return errors.New("Yggdrasil bridge already started")
	}
	if len(configJSON) == 0 {
		b.mutex.Unlock()
		return errors.New("Yggdrasil configuration is empty")
	}
	b.mutex.Unlock()
	if err := b.startLocked(configJSON); err != nil {
		return err
	}
	b.mutex.Lock()
	peerManagerEnabled := b.peerManagerEnabled
	b.mutex.Unlock()
	if peerManagerEnabled {
		if err := b.startPeerManagerLocked(); err != nil {
			b.Stop()
			return err
		}
	}
	return nil
}

func (b *Bridge) StartAutoconfigure() error {
	cfg, err := DefaultConfigJSON()
	if err != nil {
		return fmt.Errorf("encode default Yggdrasil configuration: %w", err)
	}
	return b.Start(cfg)
}

func (b *Bridge) GenerateNodeSeed() ([]byte, error) {
	return GenerateNodeSeed()
}

func (b *Bridge) startLocked(configJSON []byte) error {
	node, err := startConfiguredNode(configJSON)
	if err != nil {
		return err
	}
	stack, err := netstack.CreateYggdrasilNetstack(node)
	if err != nil {
		node.Stop()
		return fmt.Errorf("create Yggdrasil IP stack: %w", err)
	}
	b.mutex.Lock()
	b.node = node
	b.stack = stack
	b.running = true
	b.mutex.Unlock()
	return nil
}

func (b *Bridge) Address() (string, error) {
	b.mutex.Lock()
	defer b.mutex.Unlock()
	if !b.running {
		return "", errors.New("Yggdrasil bridge is not started")
	}
	return b.node.Address().String(), nil
}

func (b *Bridge) PublicKey() (string, error) {
	b.mutex.Lock()
	defer b.mutex.Unlock()
	if !b.running {
		return "", errors.New("Yggdrasil bridge is not started")
	}
	return hex.EncodeToString(b.node.PublicKey()), nil
}

func (b *Bridge) ConnectedPeerCount() (int, error) {
	b.mutex.Lock()
	defer b.mutex.Unlock()
	if !b.running {
		return 0, errors.New("Yggdrasil bridge is not started")
	}
	connected := 0
	for _, peer := range b.node.GetPeers() {
		if peer.Up {
			connected++
		}
	}
	return connected, nil
}

func (b *Bridge) PeerStatusJSON() (string, error) {
	b.mutex.Lock()
	defer b.mutex.Unlock()
	if !b.running {
		return "", errors.New("Yggdrasil bridge is not started")
	}
	type peerStatus struct {
		URI       string `json:"URI"`
		Up        bool   `json:"Up"`
		Inbound   bool   `json:"Inbound"`
		Port      uint64 `json:"Port"`
		Cost      uint64 `json:"Cost"`
		Latency   int64  `json:"Latency"`
		Uptime    int64  `json:"Uptime"`
		LastError string `json:"LastError,omitempty"`
	}
	peers := b.node.GetPeers()
	statuses := make([]peerStatus, 0, len(peers))
	for _, peer := range peers {
		status := peerStatus{
			URI: peer.URI, Up: peer.Up, Inbound: peer.Inbound, Port: peer.Port,
			Cost: peer.Cost, Latency: int64(peer.Latency), Uptime: int64(peer.Uptime),
		}
		if peer.LastError != nil {
			status.LastError = peer.LastError.Error()
		}
		statuses = append(statuses, status)
	}
	encoded, err := json.Marshal(statuses)
	if err != nil {
		return "", fmt.Errorf("encode Yggdrasil peer status: %w", err)
	}
	return string(encoded), nil
}

func (b *Bridge) PeerPoolJSON() (string, error) {
	b.mutex.Lock()
	manager := b.peerManager
	running := b.running
	b.mutex.Unlock()
	if !running {
		return "", errors.New("Yggdrasil bridge is not started")
	}
	if manager == nil {
		return `{"peers":[],"selfLat":null,"selfLon":null}`, nil
	}
	return manager.PeerPoolJSON()
}

func (b *Bridge) PeersJSON() (string, error) {
	b.mutex.Lock()
	defer b.mutex.Unlock()
	if !b.running {
		return "", errors.New("Yggdrasil bridge is not started")
	}
	peers, err := json.Marshal(b.node.GetPeers())
	if err != nil {
		return "", fmt.Errorf("encode Yggdrasil peers: %w", err)
	}
	return string(peers), nil
}

func (b *Bridge) DialTCP(address string, port int) (int32, error) {
	if err := validateEndpoint(address, port); err != nil {
		return 0, err
	}
	b.mutex.Lock()
	if b.stack == nil {
		b.mutex.Unlock()
		return 0, errors.New("Yggdrasil bridge is not started")
	}
	stack := b.stack
	b.mutex.Unlock()
	conn, err := stack.DialTCP(&net.TCPAddr{IP: net.ParseIP(address), Port: port})
	if err != nil {
		return 0, fmt.Errorf("dial Yggdrasil TCP endpoint: %w", err)
	}
	return b.storeConnection(conn)
}

func (b *Bridge) DialUDP(address string, port int) (int32, error) {
	if err := validateEndpoint(address, port); err != nil {
		return 0, err
	}
	b.mutex.Lock()
	if b.stack == nil {
		b.mutex.Unlock()
		return 0, errors.New("Yggdrasil bridge is not started")
	}
	stack := b.stack
	b.mutex.Unlock()
	conn, err := stack.DialUDP(&net.UDPAddr{IP: net.ParseIP(address), Port: port})
	if err != nil {
		return 0, fmt.Errorf("dial Yggdrasil UDP endpoint: %w", err)
	}
	return b.storeConnection(conn)
}

func (b *Bridge) Write(connectionID int32, payload []byte) error {
	conn, err := b.connection(connectionID)
	if err != nil {
		return err
	}
	if _, err := conn.Write(payload); err != nil {
		return fmt.Errorf("write Yggdrasil transport payload: %w", err)
	}
	return nil
}

func (b *Bridge) WriteDatagram(connectionID int32, payload []byte) error {
	conn, err := b.connection(connectionID)
	if err != nil {
		return err
	}
	udpConn, ok := conn.(*gonet.UDPConn)
	if !ok {
		return errors.New("connection is not UDP")
	}
	if _, err := udpConn.Write(payload); err != nil {
		return fmt.Errorf("write Yggdrasil UDP datagram: %w", err)
	}
	return nil
}

func (b *Bridge) WriteCallMediaDatagram(connectionID int32, payload []byte) error {
	if len(payload) == 0 || len(payload) > 1200 {
		return errors.New("CALL_MEDIA datagram must be between 1 and 1200 bytes")
	}
	return b.WriteDatagram(connectionID, payload)
}

func (b *Bridge) Read(connectionID int32, maxBytes int, timeoutMillis int) ([]byte, error) {
	if maxBytes < 1 || maxBytes > 1_048_576 {
		return nil, errors.New("read size must be between 1 and 1048576 bytes")
	}
	if timeoutMillis < 0 || timeoutMillis > 300_000 {
		return nil, errors.New("read timeout must be between 0 and 300000 milliseconds")
	}
	conn, err := b.connection(connectionID)
	if err != nil {
		return nil, err
	}
	if timeoutMillis > 0 {
		if err := conn.SetReadDeadline(time.Now().Add(time.Duration(timeoutMillis) * time.Millisecond)); err != nil {
			return nil, fmt.Errorf("set Yggdrasil read deadline: %w", err)
		}
	}
	if udpConn, ok := conn.(*gonet.UDPConn); ok {
		buffer := make([]byte, maxBytes)
		n, _, err := udpConn.ReadFrom(buffer)
		if err != nil {
			return nil, fmt.Errorf("read Yggdrasil UDP datagram: %w", err)
		}
		return buffer[:n], nil
	}
	buffer := make([]byte, maxBytes)
	n, err := conn.Read(buffer)
	if err != nil {
		return nil, fmt.Errorf("read Yggdrasil transport payload: %w", err)
	}
	return buffer[:n], nil
}

func (b *Bridge) CloseConnection(connectionID int32) error {
	b.mutex.Lock()
	conn, ok := b.connections[connectionID]
	if ok {
		delete(b.connections, connectionID)
	}
	b.mutex.Unlock()
	if !ok {
		return fmt.Errorf("unknown Yggdrasil connection ID %d", connectionID)
	}
	if err := conn.Close(); err != nil {
		return fmt.Errorf("close Yggdrasil connection: %w", err)
	}
	return nil
}

func (b *Bridge) Close() error {
	return b.Stop()
}

func (b *Bridge) RetryPeers() error {
	b.mutex.Lock()
	defer b.mutex.Unlock()
	if !b.running {
		return errors.New("Yggdrasil bridge is not started")
	}
	b.node.RetryPeersNow()
	return nil
}

func ProbePeer(uri string) (int64, error) {
	parsed, err := url.Parse(uri)
	if err != nil || (parsed.Scheme != "tcp" && parsed.Scheme != "tls") || parsed.Hostname() == "" {
		return 0, errors.New("invalid Yggdrasil peer URI")
	}
	port := parsed.Port()
	portNumber, err := strconv.Atoi(port)
	if err != nil || portNumber < 1 || portNumber > 65535 {
		return 0, errors.New("invalid Yggdrasil peer port")
	}
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	startedAt := time.Now()
	conn, err := (&net.Dialer{}).DialContext(ctx, "tcp", net.JoinHostPort(parsed.Hostname(), port))
	if err != nil {
		return 0, fmt.Errorf("probe Yggdrasil peer: %w", err)
	}
	if err := conn.Close(); err != nil {
		return 0, fmt.Errorf("close Yggdrasil peer probe: %w", err)
	}
	return time.Since(startedAt).Milliseconds(), nil
}

func ProbePeerLatency(uri string) int64 {
	latency, err := ProbePeer(uri)
	if err != nil {
		return -1
	}
	return latency
}

func (b *Bridge) ReplacePeers(peersJSON string) error {
	parsedPeers, err := parseReplacementPeers(peersJSON)
	if err != nil {
		return err
	}
	if len(parsedPeers) < 2 || len(parsedPeers) > 3 {
		return errors.New("replacement peer list must contain between two and three peers")
	}
	b.mutex.Lock()
	if !b.running {
		b.mutex.Unlock()
		return errors.New("Yggdrasil bridge is not started")
	}
	node := b.node
	manager := b.peerManager
	b.mutex.Unlock()
	if manager != nil {
		return manager.ReplacePeers(parsedPeers)
	}
	if err := replaceConfiguredPeers(node, parsedPeers); err != nil {
		return err
	}
	node.RetryPeersNow()
	return nil
}

func (b *Bridge) ReplacePeersWithoutRetry(peersJSON string) error {
	return b.replaceConfiguredPeerList(peersJSON, false)
}

func (b *Bridge) replaceConfiguredPeerList(peersJSON string, retry bool) error {
	parsedPeers, err := parseReplacementPeers(peersJSON)
	if err != nil {
		return err
	}
	if len(parsedPeers) < 2 || len(parsedPeers) > 3 {
		return errors.New("configured peer list must contain between two and three peers")
	}
	b.mutex.Lock()
	if !b.running {
		b.mutex.Unlock()
		return errors.New("Yggdrasil bridge is not started")
	}
	node := b.node
	manager := b.peerManager
	b.mutex.Unlock()
	if manager != nil {
		if err := manager.ReplacePeersWithoutRetry(parsedPeers); err != nil {
			return err
		}
		if retry {
			node.RetryPeersNow()
		}
		return nil
	}
	if err := replaceConfiguredPeers(node, parsedPeers); err != nil {
		return err
	}
	if retry {
		node.RetryPeersNow()
	}
	return nil
}

func (b *Bridge) RefreshPeerPool() error {
	b.mutex.Lock()
	manager := b.peerManager
	started := b.running
	b.mutex.Unlock()
	if !started || manager == nil {
		return errors.New("background peer manager is not started")
	}
	return manager.RefreshPeerPool(context.Background())
}

func (b *Bridge) RefreshAndReconcilePeers() error {
	b.mutex.Lock()
	manager := b.peerManager
	started := b.running
	b.mutex.Unlock()
	if !started || manager == nil {
		return errors.New("background peer manager is not started")
	}
	return manager.RefreshAndReconcile(context.Background())
}

func (b *Bridge) ConfiguredPeerURIsJSON() (string, error) {
	b.mutex.Lock()
	if !b.running {
		b.mutex.Unlock()
		return "", errors.New("Yggdrasil bridge is not started")
	}
	node := b.node
	b.mutex.Unlock()
	values := peerURIStrings(configuredPeers(node.GetPeers()))
	encoded, err := json.Marshal(values)
	if err != nil {
		return "", fmt.Errorf("encode configured peer list: %w", err)
	}
	return string(encoded), nil
}

func (b *Bridge) startPeerManagerLocked() error {
	b.mutex.Lock()
	node := b.node
	if !b.running || node == nil {
		b.mutex.Unlock()
		return errors.New("Yggdrasil bridge is not started")
	}
	manager := NewPeerManager(node, replaceConfiguredPeers)
	b.peerManager = manager
	b.mutex.Unlock()
	if err := manager.Start(); err != nil {
		b.mutex.Lock()
		if b.peerManager == manager {
			b.peerManager = nil
		}
		b.mutex.Unlock()
		return fmt.Errorf("start background peer manager: %w", err)
	}
	return nil
}

func (b *Bridge) EnablePeerManager() {
	b.mutex.Lock()
	b.peerManagerEnabled = true
	b.mutex.Unlock()
}

func parseReplacementPeers(peersJSON string) ([]*url.URL, error) {
	var peerURIs []string
	if err := json.Unmarshal([]byte(peersJSON), &peerURIs); err != nil || peerURIs == nil {
		return nil, errors.New("invalid replacement peer list")
	}
	if len(peerURIs) < 2 || len(peerURIs) > 3 {
		return nil, errors.New("replacement peer list must contain between two and three peers")
	}
	parsedPeers := make([]*url.URL, 0, len(peerURIs))
	seen := make(map[string]struct{}, len(peerURIs))
	for _, peerURI := range peerURIs {
		parsed, err := url.Parse(peerURI)
		if err != nil {
			return nil, fmt.Errorf("parse replacement peer: %w", err)
		}
		if parsed.Scheme != "tcp" && parsed.Scheme != "tls" {
			return nil, fmt.Errorf("invalid replacement peer URI %q", peerURI)
		}
		if port, err := strconv.Atoi(parsed.Port()); err != nil || port < 1 || port > 65535 {
			return nil, fmt.Errorf("invalid replacement peer port in %q", peerURI)
		}
		key := normalizedPeerURI(parsed)
		if _, exists := seen[key]; exists {
			return nil, fmt.Errorf("duplicate replacement peer URI %q", peerURI)
		}
		seen[key] = struct{}{}
		parsedPeers = append(parsedPeers, parsed)
	}
	return parsedPeers, nil
}

func replaceConfiguredPeers(node *core.Core, replacements []*url.URL) error {
	currentPeers := node.GetPeers()
	rollbackPeers := configuredPeers(currentPeers)
	targets := make(map[string]struct{}, len(replacements))
	for _, peer := range replacements {
		targets[normalizedPeerURI(peer)] = struct{}{}
	}
	currentTargets := make(map[string]struct{}, len(currentPeers))
	for _, peer := range currentPeers {
		if peer.Inbound {
			continue
		}
		parsed, err := url.Parse(peer.URI)
		if err != nil {
			return fmt.Errorf("parse configured peer URI %q: %w", peer.URI, err)
		}
		currentTargets[normalizedPeerURI(parsed)] = struct{}{}
	}
	removedPeers := make([]*url.URL, 0, len(rollbackPeers))
	for _, peer := range currentPeers {
		if peer.Inbound {
			continue
		}
		parsed, err := url.Parse(peer.URI)
		if err != nil {
			return fmt.Errorf("parse configured peer URI %q: %w", peer.URI, err)
		}
		if _, retain := targets[normalizedPeerURI(parsed)]; retain {
			continue
		}
		removeURI := *parsed
		removeURI.RawQuery = ""
		if err := node.RemovePeer(&removeURI, ""); err != nil && !errors.Is(err, core.ErrLinkNotConfigured) {
			return fmt.Errorf("remove configured peer %q: %w", peer.URI, err)
		}
		removedPeers = append(removedPeers, &removeURI)
	}
	addedPeers := make([]*url.URL, 0, len(replacements))
	for _, peer := range replacements {
		if _, exists := currentTargets[normalizedPeerURI(peer)]; exists {
			if err := node.AddPeer(peer, ""); err != nil && !errors.Is(err, core.ErrLinkAlreadyConfigured) {
				return fmt.Errorf("retry configured peer %q: %w", peer.String(), err)
			}
			continue
		}
		if err := node.AddPeer(peer, ""); err != nil {
			if errors.Is(err, core.ErrLinkAlreadyConfigured) {
				continue
			}
			if rollbackErr := rollbackPeerUpdate(node, addedPeers, removedPeers, rollbackPeers); rollbackErr != nil {
				return fmt.Errorf("add replacement peer %q: %w; restore previous peers: %v", peer.String(), err, rollbackErr)
			}
			return fmt.Errorf("add replacement peer %q: %w", peer.String(), err)
		}
		addedPeers = append(addedPeers, peer)
	}
	return nil
}

func configuredPeers(peers []core.PeerInfo) []*url.URL {
	configured := make([]*url.URL, 0, len(peers))
	for _, peer := range peers {
		if peer.Inbound {
			continue
		}
		parsed, err := url.Parse(peer.URI)
		if err == nil {
			parsed.RawQuery = ""
			configured = append(configured, parsed)
		}
	}
	return configured
}

func rollbackPeerUpdate(node *core.Core, replacements, removed, previous []*url.URL) error {
	for _, peer := range replacements {
		removeURI := *peer
		removeURI.RawQuery = ""
		if err := node.RemovePeer(&removeURI, ""); err != nil && !errors.Is(err, core.ErrLinkNotConfigured) {
			return fmt.Errorf("remove replacement peer %q: %w", peer.String(), err)
		}
	}
	if err := restoreRemovedPeers(node, removed); err != nil {
		return err
	}
	return restoreRemovedPeers(node, previous)
}

func restoreRemovedPeers(node *core.Core, previous []*url.URL) error {
	for _, peer := range previous {
		if err := node.AddPeer(peer, ""); err != nil {
			return fmt.Errorf("restore peer %q: %w", peer.String(), err)
		}
	}
	return nil
}

func (b *Bridge) Stop() error {
	b.lifecycle.Lock()
	defer b.lifecycle.Unlock()
	b.mutex.Lock()
	manager := b.peerManager
	b.peerManager = nil
	b.mutex.Unlock()
	if manager != nil {
		manager.Stop()
	}
	b.mutex.Lock()
	connections := b.connections
	listeners := b.listeners
	node := b.node
	b.connections = make(map[int32]net.Conn)
	b.listeners = make(map[int32]*listenerState)
	b.node = nil
	b.stack = nil
	b.running = false
	b.mutex.Unlock()
	for _, state := range listeners {
		closeListenerState(state)
	}
	var closeErr error
	for _, conn := range connections {
		if err := conn.Close(); err != nil && closeErr == nil {
			closeErr = fmt.Errorf("close Yggdrasil connection: %w", err)
		}
	}
	if node != nil {
		node.Stop()
	}
	return closeErr
}

func (b *Bridge) storeConnection(conn net.Conn) (int32, error) {
	b.mutex.Lock()
	defer b.mutex.Unlock()
	if !b.running {
		conn.Close()
		return 0, errors.New("Yggdrasil bridge stopped while opening connection")
	}
	var randomID [4]byte
	if _, err := rand.Read(randomID[:]); err != nil {
		conn.Close()
		return 0, fmt.Errorf("generate Yggdrasil connection ID: %w", err)
	}
	connectionID := int32(randomID[0])<<24 | int32(randomID[1])<<16 | int32(randomID[2])<<8 | int32(randomID[3])
	for connectionID == 0 || b.connections[connectionID] != nil {
		connectionID++
	}
	b.connections[connectionID] = conn
	return connectionID, nil
}

func (b *Bridge) connection(connectionID int32) (net.Conn, error) {
	b.mutex.Lock()
	defer b.mutex.Unlock()
	if b.node == nil {
		return nil, errors.New("Yggdrasil bridge is not started")
	}
	conn, ok := b.connections[connectionID]
	if !ok {
		return nil, fmt.Errorf("unknown Yggdrasil connection ID %d", connectionID)
	}
	return conn, nil
}
