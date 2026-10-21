package transportbridge

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"net/http/httptest"
	"net/url"
	"sync"
	"testing"
	"time"

	"github.com/yggdrasil-network/yggdrasil-go/src/core"
)

func TestManagedPeerScoreMatchesDesktopWeights(t *testing.T) {
	latency := int64(600)
	peer := peerCandidate{UptimePct: 90, LatencyMs: &latency, DistanceKm: 3000, HasDistance: true, Alive: true}
	if got, want := computeCandidateScore(peer), 80.0; got != want {
		t.Fatalf("candidate score = %v, want %v", got, want)
	}
}

func TestManagedPeerScoreUsesDesktopUnknownDistanceFallback(t *testing.T) {
	latency := int64(600)
	peer := peerCandidate{UptimePct: 90, LatencyMs: &latency, Alive: true}
	if got, want := computeCandidateScore(peer), 50.0; got != want {
		t.Fatalf("candidate score without geolocation = %v, want %v", got, want)
	}
}

func TestSelectReachablePeerPoolLimitsAndSortsByScore(t *testing.T) {
	candidates := make([]peerCandidate, maxReservePeers+1)
	for index := range candidates {
		candidates[index] = peerCandidate{URI: fmt.Sprintf("tls://peer.example:%d", index+1), Alive: true, Score: float64(index)}
	}
	selected := selectReachablePeers(candidates, maxReservePeers)
	if len(selected) != maxReservePeers {
		t.Fatalf("reachable reserve count = %d, want %d", len(selected), maxReservePeers)
	}
	if selected[0].Score <= selected[len(selected)-1].Score {
		t.Fatal("reachable peers were not sorted by descending score")
	}
}

func TestSelectReachablePeerPoolOmitsUnreachableCandidates(t *testing.T) {
	selected := selectReachablePeers([]peerCandidate{
		{URI: "tls://down.example:443", Alive: false, Score: 100},
		{URI: "tls://up.example:443", Alive: true, Score: 1},
	}, maxReservePeers)
	if len(selected) != 1 || selected[0].URI != "tls://up.example:443" {
		t.Fatalf("reachable pool = %#v", selected)
	}
}

func TestPeerManagerPeerPoolJSONReturnsDiscoveredReserve(t *testing.T) {
	manager := NewPeerManager(nil, nil)
	latency := int64(18)
	manager.pool = []peerCandidate{{URI: "tls://reserve.example:443", Country: "ES", UptimePct: 95, LatencyMs: &latency, Score: 80, Alive: true}}
	encoded, err := manager.PeerPoolJSON()
	if err != nil {
		t.Fatal(err)
	}
	var peers []peerCandidate
	if err := json.Unmarshal([]byte(encoded), &peers); err != nil {
		t.Fatal(err)
	}
	if len(peers) != 1 || peers[0].URI != "tls://reserve.example:443" || !peers[0].Alive || peers[0].LatencyMs == nil || *peers[0].LatencyMs != 18 {
		t.Fatalf("peer pool snapshot = %#v", peers)
	}
}

func TestMergePeerCandidatesUsesAPIRecordsAndFallbacks(t *testing.T) {
	merged := mergePeerCandidates([]peerCandidate{
		{URI: "tls://directory.example:443", Country: "ES", UptimePct: 101, Status: "up"},
		{URI: "tls://offline.example:443", Status: "down"},
	})
	if len(merged) != len(fallbackPeers)+1 {
		t.Fatalf("candidate count = %d, want %d", len(merged), len(fallbackPeers)+1)
	}
	for _, peer := range merged {
		if peer.URI == "tls://directory.example:443" && peer.UptimePct != 100 {
			t.Fatalf("directory uptime was not clamped: %v", peer.UptimePct)
		}
		if peer.URI == "tls://offline.example:443" {
			t.Fatal("offline directory peer was included")
		}
	}
}

func TestPeerManagerProbeReplacesFailedConfiguredPeerFromBestReserve(t *testing.T) {
	var mu sync.Mutex
	var replaced []*url.URL
	manager := NewPeerManager(nil, func(_ *core.Core, peers []*url.URL) error {
		mu.Lock()
		replaced = append([]*url.URL(nil), peers...)
		mu.Unlock()
		return nil
	})
	current := []*url.URL{mustManagerPeerURL(t, "tcp://127.0.0.1:1"), mustManagerPeerURL(t, "tcp://127.0.0.1:2")}
	refresh := func(context.Context) ([]peerCandidate, error) {
		return []peerCandidate{
			{URI: "tls://slow.example:443", Alive: true, Score: 40},
			{URI: "tls://fast.example:443", Alive: true, Score: 80},
		}, nil
	}
	manager.probe = func(_ context.Context, uri string) (int64, error) {
		if uri == "tcp://127.0.0.1:1" {
			return 0, errors.New("peer down")
		}
		return 5, nil
	}
	manager.fetch = func(context.Context) ([]peerCandidate, error) { return nil, nil }
	manager.resolve = func(context.Context, []peerCandidate, *http.Client) {}
	if err := reconcileConfiguredPeers(context.Background(), current, refresh, manager.probe, manager.replace, manager.node); err != nil {
		t.Fatal(err)
	}
	mu.Lock()
	defer mu.Unlock()
	got := make([]string, len(replaced))
	for index, peer := range replaced {
		got[index] = peer.String()
	}
	if len(got) != 3 || got[0] != "tcp://127.0.0.1:2" || got[1] != "tls://fast.example:443" || got[2] != "tls://slow.example:443" {
		t.Fatalf("replacement peer URIs = %#v", got)
	}
}

func TestPeerManagerStartAndStopAreGuarded(t *testing.T) {
	if err := NewPeerManager(nil, nil).Start(); err == nil {
		t.Fatal("peer manager started without its node and replacement function")
	}
	bridge := NewBridge()
	if err := bridge.StartAutoconfigure(); err != nil {
		t.Fatal(err)
	}
	defer bridge.Stop()
	manager := NewPeerManager(bridge.node, replaceConfiguredPeers)
	manager.probe = func(context.Context, string) (int64, error) { return 0, errors.New("unreachable") }
	manager.resolve = func(context.Context, []peerCandidate, *http.Client) {}
	manager.pool = []peerCandidate{{URI: "tls://reserve.example:443", Alive: true, Score: 80}, {URI: "tcp://reserve2.example:1234", Alive: true, Score: 70}}
	if err := manager.Start(); err != nil {
		t.Fatal(err)
	}
	if err := manager.Start(); err == nil {
		t.Fatal("peer manager allowed a duplicate start")
	}
	done := manager.done
	manager.Stop()
	select {
	case <-done:
	case <-time.After(time.Second):
		t.Fatal("peer manager did not stop")
	}
}

func TestPeerManagerPreservesExistingPeersWhenDiscoveryCannotFindReachablePeers(t *testing.T) {
	bridge := NewBridge()
	if err := bridge.StartAutoconfigure(); err != nil {
		t.Fatal(err)
	}
	defer bridge.Stop()
	if err := bridge.node.AddPeer(mustManagerPeerURL(t, "tcp://127.0.0.1:1"), ""); err != nil {
		t.Fatal(err)
	}
	manager := NewPeerManager(bridge.node, replaceConfiguredPeers)
	manager.fetch = func(context.Context) ([]peerCandidate, error) { return nil, nil }
	manager.probe = func(context.Context, string) (int64, error) { return 0, errors.New("unreachable") }
	manager.resolve = func(context.Context, []peerCandidate, *http.Client) {}
	manager.pool = []peerCandidate{{URI: "tls://reserve.example:443", Alive: true, Score: 80}, {URI: "tcp://reserve2.example:1234", Alive: true, Score: 70}}
	if err := manager.Start(); err != nil {
		t.Fatal(err)
	}
	defer manager.Stop()
	if len(configuredPeers(bridge.node.GetPeers())) == 0 {
		t.Fatal("peer manager removed every existing configured peer")
	}
}

func TestPeerDirectoryOnlyAcceptsTCPAndTLSEndpoints(t *testing.T) {
	for _, uri := range []string{"quic://peer.example:1515", "https://peer.example:443", "tls://peer.example"} {
		if _, err := parsePeerList([]string{uri, "tcp://validation.example:1"}); err == nil {
			t.Fatalf("directory peer URI accepted: %s", uri)
		}
	}
}

func TestPeerCandidateDecodesDirectoryStatusAndUptime(t *testing.T) {
	var payload peerDirectoryResponse
	body := []byte(`{"peers":[{"uri":"tls://directory.example:443","country":"ES","status":"up","uptime_pct":99.6},{"uri":"tls://offline.example:443","status":"down","uptime_pct":50}]}`)
	if err := json.Unmarshal(body, &payload); err != nil {
		t.Fatal(err)
	}
	merged := mergePeerCandidates(payload.Peers)
	var found *peerCandidate
	for index := range merged {
		if merged[index].URI == "tls://directory.example:443" {
			found = &merged[index]
		}
		if merged[index].URI == "tls://offline.example:443" {
			t.Fatal("offline directory peer was included")
		}
	}
	if found == nil {
		t.Fatal("online directory peer was discarded")
	}
	if found.UptimePct != 99.6 {
		t.Fatalf("directory uptime = %v, want 99.6", found.UptimePct)
	}
}

func TestRefreshPoolUsesFallbacksWhenDirectoryIsUnavailable(t *testing.T) {
	manager := NewPeerManager(nil, nil)
	manager.fetch = func(context.Context) ([]peerCandidate, error) {
		return nil, errors.New("directory offline")
	}
	manager.resolve = func(context.Context, []peerCandidate, *http.Client) {}
	manager.probe = func(_ context.Context, uri string) (int64, error) {
		if uri == fallbackPeers[0].URI {
			return 5, nil
		}
		return 0, errors.New("unreachable")
	}
	if err := manager.refreshPool(context.Background()); err != nil {
		t.Fatal(err)
	}
	if len(manager.pool) != 1 || manager.pool[0].URI != fallbackPeers[0].URI {
		t.Fatalf("fallback pool = %#v", manager.pool)
	}
	if !manager.pool[0].Alive || manager.pool[0].LatencyMs == nil || *manager.pool[0].LatencyMs != 5 {
		t.Fatalf("fallback probe result = %#v", manager.pool[0])
	}
}

func TestInitializePeerListDefersWhenNoPeerIsReachable(t *testing.T) {
	manager := NewPeerManager(nil, func(*core.Core, []*url.URL) error { return nil })
	manager.fetch = func(context.Context) ([]peerCandidate, error) { return nil, nil }
	manager.resolve = func(context.Context, []peerCandidate, *http.Client) {}
	manager.probe = func(context.Context, string) (int64, error) { return 0, errors.New("unreachable") }
	if err := manager.initializePeerList(context.Background()); err == nil {
		t.Fatal("initial peer selection should be deferred when no peer is reachable")
	}
}

func TestPeerManagerStartSurvivesUnreachableDirectory(t *testing.T) {
	bridge := NewBridge()
	if err := bridge.StartAutoconfigure(); err != nil {
		t.Fatal(err)
	}
	defer bridge.Stop()
	manager := NewPeerManager(bridge.node, replaceConfiguredPeers)
	manager.fetch = func(context.Context) ([]peerCandidate, error) { return nil, errors.New("directory offline") }
	manager.resolve = func(context.Context, []peerCandidate, *http.Client) {}
	manager.probe = func(context.Context, string) (int64, error) { return 0, errors.New("unreachable") }
	if err := manager.Start(); err != nil {
		t.Fatalf("peer manager aborted without reachable peers: %v", err)
	}
	defer manager.Stop()
	if len(configuredPeers(bridge.node.GetPeers())) != 0 {
		t.Fatal("peer manager configured peers without a reachable candidate")
	}
}

func TestFetchCoordinatesParsesIPWhoIsResponse(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, _ *http.Request) {
		writer.Header().Set("Content-Type", "application/json")
		fmt.Fprint(writer, `{"success":true,"latitude":48.8534,"longitude":2.3488}`)
	}))
	defer server.Close()
	coordinates, err := fetchCoordinates(context.Background(), server.Client(), server.URL)
	if err != nil {
		t.Fatal(err)
	}
	if coordinates.Lat != 48.8534 || coordinates.Lon != 2.3488 {
		t.Fatalf("coordinates = %#v", coordinates)
	}
}

func TestFetchCoordinatesRejectsFailedLookup(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, _ *http.Request) {
		writer.Header().Set("Content-Type", "application/json")
		fmt.Fprint(writer, `{"success":false,"message":"reserved range"}`)
	}))
	defer server.Close()
	if _, err := fetchCoordinates(context.Background(), server.Client(), server.URL); err == nil {
		t.Fatal("geolocation failure was accepted")
	}
}

func mustManagerPeerURL(t *testing.T, value string) *url.URL {
	t.Helper()
	peer, err := url.Parse(value)
	if err != nil {
		t.Fatal(err)
	}
	return peer
}
