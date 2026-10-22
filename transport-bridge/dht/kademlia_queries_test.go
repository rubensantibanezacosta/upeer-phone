package dht

import (
	"testing"
	"time"
)

func TestKademliaQueriesResolveOnlyMatchingQueryAndTarget(t *testing.T) {
	queries := NewKademliaQueries(fixedClock(time.UnixMilli(1_000)), 0)
	if !queries.Register("query-1", "aa") {
		t.Fatal("expected query registration")
	}
	if queries.Resolve(&DhtFoundValue{QueryID: "other", Key: "aa", Value: "value"}) {
		t.Fatal("unexpected query identifier was accepted")
	}
	if queries.Resolve(&DhtFoundValue{QueryID: "query-1", Key: "bb", Value: "value"}) {
		t.Fatal("unexpected target was accepted")
	}
	response := &DhtFoundValue{QueryID: "query-1", Key: "aa", Value: "value"}
	if !queries.Resolve(response) {
		t.Fatal("expected matching response to resolve")
	}
	if queries.Resolve(response) {
		t.Fatal("duplicate response was accepted")
	}
	message, ok := queries.Wait("query-1", 20*time.Millisecond)
	if !ok || message != response {
		t.Fatal("resolved response was not preserved for the caller")
	}
}

func TestKademliaQueriesTimeOutAndCleanup(t *testing.T) {
	now := time.UnixMilli(1_000)
	queries := NewKademliaQueries(func() time.Time { return now }, 100*time.Millisecond)
	if !queries.Register("query-1", "") {
		t.Fatal("expected query registration")
	}
	if _, ok := queries.Wait("query-1", 10*time.Millisecond); ok {
		t.Fatal("expected query timeout")
	}
	if queries.Resolve(&DhtPong{QueryID: "query-1"}) {
		t.Fatal("timed out query must not resolve")
	}
	if !queries.Register("query-2", "") {
		t.Fatal("expected query registration")
	}
	now = now.Add(101 * time.Millisecond)
	if removed := queries.Cleanup(); removed != 1 {
		t.Fatalf("expected one cleanup, got %d", removed)
	}
}
