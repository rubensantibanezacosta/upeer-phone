package transportbridge

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"math"
	"net"
	"net/http"
	"net/url"
	"sort"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/yggdrasil-network/yggdrasil-go/src/core"
)

const (
	peerProbeInterval     = 5 * time.Minute
	peerDiscoveryInterval = 30 * time.Minute
	peerProbeTimeout      = 3 * time.Second
	peerProbeConcurrency  = 30
	maxActivePeers        = 3
	maxReservePeers       = 200
	maxGeoPeers           = 30
	geoCacheTTL           = 24 * time.Hour
	publicPeersURL        = "https://yggpeers.fsky.io/api/v1/peers?status=up&is_network=false"
)

var fallbackPeers = []peerCandidate{
	{URI: "tls://ygg.mkg20001.io:443", Country: "Germany", UptimePct: 90},
	{URI: "tls://yggdrasil.neilalexander.dev:64648?key=ecbbcb3298e7d3b4196103333c3e839cfe47a6ca47602b94a6d596683f6bb358", Country: "UK", UptimePct: 90},
	{URI: "tls://51.15.204.214:54321", Country: "France", UptimePct: 90},
	{URI: "tls://95.217.35.92:1337", Country: "Finland", UptimePct: 90},
	{URI: "tls://vpn.ltha.de:443", Country: "Germany", UptimePct: 90},
	{URI: "tcp://longseason.1200bps.xyz:13121", Country: "UK", UptimePct: 90},
	{URI: "tls://ygg1.grin.hu:42444", Country: "Hungary", UptimePct: 90},
	{URI: "tcp://yggno.de:18226", Country: "Germany", UptimePct: 90},
	{URI: "tls://spain.magicum.net:36901", Country: "Spain", UptimePct: 90},
	{URI: "tls://redcatho.de:9494", Country: "Germany", UptimePct: 90},
}

type peerCandidate struct {
	URI         string   `json:"uri"`
	Country     string   `json:"country"`
	UptimePct   float64  `json:"uptime_pct"`
	Status      string   `json:"status"`
	LatencyMs   *int64   `json:"latencyMs"`
	DistanceKm  float64  `json:"distanceKm"`
	HasDistance bool     `json:"hasDistance"`
	Score       float64  `json:"score"`
	Alive       bool     `json:"alive"`
	Host        string   `json:"host"`
	Lat         *float64 `json:"lat"`
	Lon         *float64 `json:"lon"`
}

type peerDirectoryResponse struct {
	Peers []peerCandidate `json:"peers"`
}

type peerReplacement func(*core.Core, []*url.URL) error

type PeerManager struct {
	node                *core.Core
	replace             peerReplacement
	mu                  sync.Mutex
	pool                []peerCandidate
	active              []string
	operation           sync.Mutex
	cancel              context.CancelFunc
	done                chan struct{}
	probe               peerProbe
	refreshCandidatesFn func(context.Context) ([]peerCandidate, error)
	fetch               peerFetcher
	resolve             peerGeoResolver
	client              *http.Client
	geoMu               sync.Mutex
	geoCache            map[string]geoCacheEntry
}

type peerProbe func(context.Context, string) (int64, error)
type peerFetcher func(context.Context) ([]peerCandidate, error)
type peerGeoResolver func(context.Context, []peerCandidate, *http.Client)

func NewPeerManager(node *core.Core, replace peerReplacement) *PeerManager {
	client := &http.Client{Timeout: 8 * time.Second}
	manager := &PeerManager{
		node:    node,
		replace: replace,
		probe:   probeCandidate,
		fetch: func(ctx context.Context) ([]peerCandidate, error) {
			return fetchPeerDirectory(ctx, client)
		},
		client:   client,
		geoCache: make(map[string]geoCacheEntry),
	}
	manager.resolve = manager.resolveCandidateDistances
	manager.refreshCandidatesFn = manager.discoverCandidates
	return manager
}

func (m *PeerManager) ReplacePeers(peers []*url.URL) error {
	if m == nil || m.node == nil {
		return errors.New("peer manager node is unavailable")
	}
	if err := m.ReplacePeersWithoutRetry(peers); err != nil {
		return err
	}
	m.node.RetryPeersNow()
	return nil
}

func (m *PeerManager) ReplacePeersWithoutRetry(peers []*url.URL) error {
	if m == nil || m.node == nil || m.replace == nil {
		return errors.New("peer manager replacement function is unavailable")
	}
	m.operation.Lock()
	defer m.operation.Unlock()
	if err := m.replace(m.node, peers); err != nil {
		return err
	}
	m.setActiveURIs(peers)
	return nil
}

func (m *PeerManager) Start() error {
	if m.node == nil || m.replace == nil {
		return errors.New("peer manager requires a node and replacement function")
	}
	m.mu.Lock()
	if m.cancel != nil {
		m.mu.Unlock()
		return errors.New("peer manager already started")
	}
	ctx, cancel := context.WithCancel(context.Background())
	m.cancel = cancel
	m.done = make(chan struct{})
	m.mu.Unlock()

	go m.run(ctx)
	fmt.Printf("uPeer peer manager starting: configured=%d\n", len(configuredPeers(m.node.GetPeers())))
	if len(configuredPeers(m.node.GetPeers())) == 0 {
		if err := m.initializePeerList(ctx); err != nil {
			fmt.Printf("uPeer initial peer selection deferred: %v\n", err)
		}
	} else if err := m.refreshPool(ctx); err != nil {
		fmt.Printf("uPeer initial peer pool refresh failed; keeping configured peers: %v\n", err)
	}
	return nil
}

func (m *PeerManager) StartWithInitialPeers(peers []string) error {
	if len(peers) > 0 {
		return errors.New("initial peers must be empty when peer manager selects them")
	}
	return m.Start()
}

func (m *PeerManager) initializePeerList(ctx context.Context) error {
	if err := m.refreshPool(ctx); err != nil {
		return err
	}
	return m.selectInitialPeers()
}

func (m *PeerManager) selectInitialPeers() error {
	m.mu.Lock()
	pool := append([]peerCandidate(nil), m.pool...)
	m.mu.Unlock()
	selected := selectTopURIs(pool, maxActivePeers)
	if len(selected) < 2 {
		return errors.New("fewer than two reachable Yggdrasil peers found")
	}
	peers, err := parseReplacementPeers(mustJSON(selected))
	if err != nil {
		return err
	}
	if err := m.replace(m.node, peers); err != nil {
		return fmt.Errorf("configure initial Yggdrasil peers: %w", err)
	}
	m.setActiveURIs(peers)
	m.node.RetryPeersNow()
	return nil
}

func (m *PeerManager) Stop() {
	m.mu.Lock()
	cancel := m.cancel
	done := m.done
	m.mu.Unlock()
	if cancel == nil {
		return
	}
	cancel()
	<-done
	m.mu.Lock()
	m.cancel = nil
	m.done = nil
	m.mu.Unlock()
}

func (m *PeerManager) stopStartedManager() {
	m.mu.Lock()
	cancel := m.cancel
	done := m.done
	m.mu.Unlock()
	if cancel != nil {
		cancel()
		<-done
	}
	m.mu.Lock()
	m.cancel = nil
	m.done = nil
	m.mu.Unlock()
}

func (m *PeerManager) run(ctx context.Context) {
	defer close(m.done)
	probeTicker := time.NewTicker(peerProbeInterval)
	discoveryTicker := time.NewTicker(peerDiscoveryInterval)
	defer probeTicker.Stop()
	defer discoveryTicker.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case <-probeTicker.C:
			if len(configuredPeers(m.node.GetPeers())) == 0 {
				if err := m.initializePeerList(ctx); err != nil && ctx.Err() == nil {
					fmt.Printf("uPeer peer selection retry failed: %v\n", err)
				}
				continue
			}
			if err := m.ReconcileConfiguredPeers(ctx); err != nil && ctx.Err() == nil {
				fmt.Printf("uPeer peer health check failed: %v\n", err)
			}
		case <-discoveryTicker.C:
			if err := m.refreshPool(ctx); err != nil && ctx.Err() == nil {
				fmt.Printf("uPeer peer discovery refresh failed: %v\n", err)
			}
		}
	}
}

func (m *PeerManager) checkActivePeers(ctx context.Context) error {
	m.operation.Lock()
	defer m.operation.Unlock()
	probeCtx, cancel := context.WithCancel(ctx)
	defer cancel()
	configured := configuredPeers(m.node.GetPeers())
	err := reconcileConfiguredPeers(probeCtx, configured, m.refreshCandidatesFn, m.probe, m.replace, m.node)
	if err == nil {
		m.setActiveURIs(configuredPeers(m.node.GetPeers()))
	}
	return err
}

func reconcileConfiguredPeers(ctx context.Context, configured []*url.URL, refresh func(context.Context) ([]peerCandidate, error), probe peerProbe, replace peerReplacement, node *core.Core) error {
	failed := make(map[string]struct{})
	for offset := 0; offset < len(configured); offset += peerProbeConcurrency {
		batch := configured[offset:min(offset+peerProbeConcurrency, len(configured))]
		type probeResult struct {
			peer *url.URL
			err  error
		}
		results := make(chan probeResult, len(batch))
		for _, peer := range batch {
			peer := peer
			go func() {
				peerCtx, cancel := context.WithTimeout(ctx, peerProbeTimeout)
				_, err := probe(peerCtx, peer.String())
				cancel()
				results <- probeResult{peer: peer, err: err}
			}()
		}
		for range batch {
			result := <-results
			if result.err != nil {
				failed[normalizedPeerURI(result.peer)] = struct{}{}
			}
		}
	}
	if len(failed) == 0 {
		return nil
	}
	reserve, err := refresh(ctx)
	if err != nil {
		return err
	}
	reserve = selectReachablePeers(reserve, maxReservePeers)
	return replaceFailedPeerList(configured, failed, reserve, replace, node)
}

func replaceFailedPeerList(configured []*url.URL, failed map[string]struct{}, pool []peerCandidate, replace peerReplacement, node *core.Core) error {
	activeSet := make(map[string]struct{}, len(configured))
	replacements := make([]*url.URL, 0, maxActivePeers)
	for _, peer := range configured {
		key := normalizedPeerURI(peer)
		activeSet[key] = struct{}{}
		if _, isFailed := failed[key]; !isFailed {
			replacements = append(replacements, peer)
		}
	}
	for _, candidate := range pool {
		if len(replacements) >= maxActivePeers {
			break
		}
		parsed, err := url.Parse(candidate.URI)
		if err != nil {
			continue
		}
		key := normalizedPeerURI(parsed)
		if _, active := activeSet[key]; active || !candidate.Alive {
			continue
		}
		parsed.RawQuery = ""
		replacements = append(replacements, parsed)
		activeSet[key] = struct{}{}
	}
	if len(replacements) == 0 {
		return errors.New("no reachable replacement peers available")
	}
	if err := replace(node, replacements); err != nil {
		return fmt.Errorf("replace down Yggdrasil peers: %w", err)
	}
	return nil
}

func (m *PeerManager) RefreshPeerPool(ctx context.Context) error {
	m.operation.Lock()
	defer m.operation.Unlock()
	return m.refreshPool(ctx)
}

func (m *PeerManager) RefreshAndReconcile(ctx context.Context) error {
	if err := m.RefreshPeerPool(ctx); err != nil {
		return err
	}
	return m.ReconcileConfiguredPeers(ctx)
}

func (m *PeerManager) ReconcileConfiguredPeers(ctx context.Context) error {
	m.operation.Lock()
	defer m.operation.Unlock()
	probeCtx, cancel := context.WithCancel(ctx)
	defer cancel()
	configured := configuredPeers(m.node.GetPeers())
	err := reconcileConfiguredPeers(probeCtx, configured, m.refreshCandidatesFn, m.probe, m.replace, m.node)
	if err == nil {
		m.setActiveURIs(configuredPeers(m.node.GetPeers()))
	}
	return err
}

func (m *PeerManager) ActivePeerURIs() []string {
	peers := configuredPeers(m.node.GetPeers())
	return peerURIStrings(peers)
}

func (m *PeerManager) PeerPoolJSON() (string, error) {
	m.mu.Lock()
	peers := append([]peerCandidate(nil), m.pool...)
	m.mu.Unlock()
	encoded, err := json.Marshal(peers)
	if err != nil {
		return "", fmt.Errorf("encode Yggdrasil peer reserve: %w", err)
	}
	return string(encoded), nil
}

func peerURIStrings(peers []*url.URL) []string {
	values := make([]string, 0, len(peers))
	for _, peer := range peers {
		values = append(values, normalizedPeerURI(peer))
	}
	return values
}

func (m *PeerManager) refreshPool(ctx context.Context) error {
	candidates, err := m.discoverCandidates(ctx)
	if err != nil {
		m.mu.Lock()
		hasPool := len(m.pool) > 0
		m.mu.Unlock()
		if hasPool {
			return fmt.Errorf("refresh peer directory: %w", err)
		}
		candidates = mergePeerCandidates(nil)
	}
	probeCandidates(ctx, candidates, m.probe)
	pool := selectReachablePeers(candidates, maxReservePeers)
	fmt.Printf("uPeer peer discovery: candidates=%d reachable=%d\n", len(candidates), len(pool))
	m.resolve(ctx, pool, m.client)
	for index := range pool {
		pool[index].Score = computeCandidateScore(pool[index])
	}
	sort.SliceStable(pool, func(i, j int) bool { return pool[i].Score > pool[j].Score })
	m.mu.Lock()
	if len(pool) > 0 || len(m.pool) == 0 {
		m.pool = pool
	}
	m.mu.Unlock()
	return nil
}

func (m *PeerManager) discoverCandidates(ctx context.Context) ([]peerCandidate, error) {
	discovered, err := m.fetch(ctx)
	if err != nil {
		fmt.Printf("uPeer peer directory request failed: %v\n", err)
		return nil, err
	}
	fmt.Printf("uPeer peer directory returned %d peers\n", len(discovered))
	return mergePeerCandidates(discovered), nil
}

func (m *PeerManager) setActiveURIs(peers []*url.URL) {
	active := make([]string, 0, len(peers))
	for _, peer := range peers {
		active = append(active, normalizedPeerURI(peer))
	}
	m.mu.Lock()
	m.active = active
	m.mu.Unlock()
}

func mergePeerCandidates(discovered []peerCandidate) []peerCandidate {
	byURI := make(map[string]peerCandidate, len(discovered)+len(fallbackPeers))
	for _, peer := range fallbackPeers {
		peer.Host = peerHostname(peer.URI)
		byURI[peer.URI] = peer
	}
	for _, peer := range discovered {
		if peer.Status != "up" {
			continue
		}
		if _, err := parsePeerList([]string{peer.URI, "tcp://validation.invalid:1"}); err != nil {
			continue
		}
		peer.UptimePct = max(0, min(100, peer.UptimePct))
		peer.Host = peerHostname(peer.URI)
		key := normalizedCandidateURI(peer.URI)
		byURI[key] = peer
	}
	result := make([]peerCandidate, 0, len(byURI))
	for _, peer := range byURI {
		if peer.Host == "" {
			peer.Host = peerHostname(peer.URI)
		}
		result = append(result, peer)
	}
	sort.SliceStable(result, func(i, j int) bool { return result[i].UptimePct > result[j].UptimePct })
	return result
}

func normalizedCandidateURI(value string) string {
	parsed, err := url.Parse(value)
	if err != nil {
		return value
	}
	parsed.Host = strings.ToLower(parsed.Host)
	return parsed.String()
}

func peerHostname(value string) string {
	parsed, err := url.Parse(value)
	if err != nil {
		return value
	}
	return parsed.Hostname()
}

func probeCandidates(ctx context.Context, candidates []peerCandidate, probe peerProbe) {
	jobs := make(chan int)
	var workers sync.WaitGroup
	count := min(peerProbeConcurrency, len(candidates))
	for range count {
		workers.Add(1)
		go func() {
			defer workers.Done()
			for index := range jobs {
				probeCtx, cancel := context.WithTimeout(ctx, peerProbeTimeout)
				latency, err := probe(probeCtx, candidates[index].URI)
				cancel()
				candidates[index].Alive = err == nil
				if err == nil {
					candidates[index].LatencyMs = &latency
				}
				candidates[index].Score = computeCandidateScore(candidates[index])
			}
		}()
	}
	for index := range candidates {
		select {
		case jobs <- index:
		case <-ctx.Done():
			close(jobs)
			workers.Wait()
			return
		}
	}
	close(jobs)
	workers.Wait()
}

func selectReachablePeers(candidates []peerCandidate, limit int) []peerCandidate {
	reachable := make([]peerCandidate, 0, len(candidates))
	for _, peer := range candidates {
		if peer.Alive {
			reachable = append(reachable, peer)
		}
	}
	sort.SliceStable(reachable, func(i, j int) bool { return reachable[i].Score > reachable[j].Score })
	if len(reachable) > limit {
		reachable = reachable[:limit]
	}
	return reachable
}

func computeCandidateScore(peer peerCandidate) float64 {
	latency := peerProbeTimeout.Milliseconds()
	if peer.Alive {
		if peer.LatencyMs != nil {
			latency = *peer.LatencyMs
		}
	}
	latencyScore := max(0, 1-float64(latency)/float64(peerProbeTimeout.Milliseconds())) * 40
	uptimeScore := max(0, min(100, peer.UptimePct)) / 100 * 20
	distance := peer.DistanceKm
	if !peer.HasDistance {
		distance = 12000
	}
	geoScore := max(0, 1-distance/12000) * 40
	return latencyScore + uptimeScore + geoScore
}

func selectTopURIs(peers []peerCandidate, limit int) []string {
	selected := make([]string, 0, min(limit, len(peers)))
	for _, peer := range peers {
		if len(selected) == limit {
			break
		}
		selected = append(selected, peer.URI)
	}
	return selected
}

func fetchPeerDirectory(ctx context.Context, client *http.Client) ([]peerCandidate, error) {
	request, err := http.NewRequestWithContext(ctx, http.MethodGet, publicPeersURL, nil)
	if err != nil {
		return nil, err
	}
	response, err := client.Do(request)
	if err != nil {
		return nil, err
	}
	defer response.Body.Close()
	if response.StatusCode < 200 || response.StatusCode >= 300 {
		return nil, fmt.Errorf("peer directory returned HTTP %d", response.StatusCode)
	}
	body, err := io.ReadAll(io.LimitReader(response.Body, 4<<20))
	if err != nil {
		return nil, err
	}
	var payload peerDirectoryResponse
	if err := json.Unmarshal(body, &payload); err != nil {
		return nil, fmt.Errorf("decode peer directory: %w", err)
	}
	return payload.Peers, nil
}

func (m *PeerManager) resolveCandidateDistances(ctx context.Context, candidates []peerCandidate, client *http.Client) {
	self, err := fetchSelfCoordinates(ctx, client)
	if err != nil {
		return
	}
	limit := min(len(candidates), maxGeoPeers)
	jobs := make(chan int)
	var workers sync.WaitGroup
	count := min(peerProbeConcurrency, limit)
	for range count {
		workers.Add(1)
		go func() {
			defer workers.Done()
			for index := range jobs {
				geoCtx, cancel := context.WithTimeout(ctx, 8*time.Second)
				peer, err := m.cachedPeerCoordinates(geoCtx, client, candidates[index].URI)
				cancel()
				if err == nil {
					distance := haversineKm(self.Lat, self.Lon, peer.Lat, peer.Lon)
					candidates[index].DistanceKm = distance
					candidates[index].HasDistance = true
					lat := peer.Lat
					lon := peer.Lon
					candidates[index].Lat = &lat
					candidates[index].Lon = &lon
				}
			}
		}()
	}
	for index := 0; index < limit; index++ {
		select {
		case jobs <- index:
		case <-ctx.Done():
			close(jobs)
			workers.Wait()
			return
		}
	}
	close(jobs)
	workers.Wait()
}

func (m *PeerManager) cachedPeerCoordinates(ctx context.Context, client *http.Client, uri string) (geoCoordinates, error) {
	key := peerHostname(uri)
	m.geoMu.Lock()
	entry, ok := m.geoCache[key]
	m.geoMu.Unlock()
	if ok && time.Now().Before(entry.expiresAt) {
		return entry.coordinates, nil
	}
	coordinates, err := fetchPeerCoordinates(ctx, client, uri)
	if err != nil {
		return geoCoordinates{}, err
	}
	m.geoMu.Lock()
	if m.geoCache == nil {
		m.geoCache = make(map[string]geoCacheEntry)
	}
	m.geoCache[key] = geoCacheEntry{coordinates: coordinates, expiresAt: time.Now().Add(geoCacheTTL)}
	m.geoMu.Unlock()
	return coordinates, nil
}

type geoCoordinates struct {
	Lat     float64 `json:"latitude"`
	Lon     float64 `json:"longitude"`
	Success bool    `json:"success"`
}

type geoCacheEntry struct {
	coordinates geoCoordinates
	expiresAt   time.Time
}

func fetchSelfCoordinates(ctx context.Context, client *http.Client) (geoCoordinates, error) {
	return fetchCoordinates(ctx, client, "https://ipwho.is/")
}

func fetchPeerCoordinates(ctx context.Context, client *http.Client, uri string) (geoCoordinates, error) {
	parsed, err := url.Parse(uri)
	if err != nil {
		return geoCoordinates{}, err
	}
	host := parsed.Hostname()
	if net.ParseIP(host) == nil {
		addresses, err := net.DefaultResolver.LookupIPAddr(ctx, host)
		if err != nil || len(addresses) == 0 {
			return geoCoordinates{}, errors.New("peer hostname did not resolve")
		}
		host = addresses[0].IP.String()
	}
	endpoint := "https://ipwho.is/" + url.PathEscape(host)
	return fetchCoordinates(ctx, client, endpoint)
}

func fetchCoordinates(ctx context.Context, client *http.Client, endpoint string) (geoCoordinates, error) {
	request, err := http.NewRequestWithContext(ctx, http.MethodGet, endpoint, nil)
	if err != nil {
		return geoCoordinates{}, err
	}
	request.Header.Set("Accept", "application/json")
	request.Header.Set("User-Agent", "uPeer/1.0")
	response, err := client.Do(request)
	if err != nil {
		return geoCoordinates{}, err
	}
	defer response.Body.Close()
	if response.StatusCode < 200 || response.StatusCode >= 300 {
		return geoCoordinates{}, fmt.Errorf("geolocation returned HTTP %d", response.StatusCode)
	}
	var coordinates geoCoordinates
	if err := json.NewDecoder(io.LimitReader(response.Body, 64<<10)).Decode(&coordinates); err != nil {
		return geoCoordinates{}, err
	}
	if !coordinates.Success {
		return geoCoordinates{}, errors.New("geolocation lookup returned no coordinates")
	}
	if coordinates.Lat < -90 || coordinates.Lat > 90 || coordinates.Lon < -180 || coordinates.Lon > 180 {
		return geoCoordinates{}, errors.New("geolocation coordinates are out of range")
	}
	return coordinates, nil
}

func haversineKm(lat1, lon1, lat2, lon2 float64) float64 {
	const earthRadiusKm = 6371
	toRadians := func(value float64) float64 { return value * 3.141592653589793 / 180 }
	deltaLat := toRadians(lat2 - lat1)
	deltaLon := toRadians(lon2 - lon1)
	latSin := math.Sin(deltaLat / 2)
	lonSin := math.Sin(deltaLon / 2)
	value := latSin*latSin + math.Cos(toRadians(lat1))*math.Cos(toRadians(lat2))*lonSin*lonSin
	if value > 1 {
		value = 1
	}
	return earthRadiusKm * 2 * math.Atan2(math.Sqrt(value), math.Sqrt(1-value))
}

func probeCandidate(ctx context.Context, uri string) (int64, error) {
	parsed, err := url.Parse(uri)
	if err != nil || (parsed.Scheme != "tcp" && parsed.Scheme != "tls") || parsed.Hostname() == "" {
		return 0, errors.New("invalid Yggdrasil peer URI")
	}
	port, err := strconv.Atoi(parsed.Port())
	if err != nil || port < 1 || port > 65535 {
		return 0, errors.New("invalid Yggdrasil peer port")
	}
	started := time.Now()
	connection, err := (&net.Dialer{Timeout: peerProbeTimeout}).DialContext(ctx, "tcp", net.JoinHostPort(parsed.Hostname(), parsed.Port()))
	if err != nil {
		return 0, err
	}
	closeErr := connection.Close()
	if closeErr != nil {
		return 0, closeErr
	}
	return time.Since(started).Milliseconds(), nil
}

func mustJSON(value any) string {
	encoded, _ := json.Marshal(value)
	return string(encoded)
}
