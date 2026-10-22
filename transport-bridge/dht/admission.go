package dht

import (
	"encoding/json"
	"fmt"
	"strings"
	"sync"
	"time"
)

type dhtRateRule struct {
	window time.Duration
	tokens float64
}

type dhtRateBucket struct {
	tokens     float64
	lastRefill time.Time
}

type DhtRateLimiter struct {
	clock        func() time.Time
	lock         sync.Mutex
	buckets      map[string]dhtRateBucket
	lastCleanup  time.Time
	limitedCount int64
}

var dhtRateRules = map[string]dhtRateRule{
	"DHT_QUERY":      {window: 30 * time.Second, tokens: 20},
	"DHT_RESPONSE":   {window: 30 * time.Second, tokens: 40},
	"DHT_UPDATE":     {window: 60 * time.Second, tokens: 40},
	"DHT_EXCHANGE":   {window: 60 * time.Second, tokens: 60},
	"DHT_FIND_NODE":  {window: 30 * time.Second, tokens: 30},
	"DHT_FIND_VALUE": {window: 30 * time.Second, tokens: 30},
	"DHT_STORE":      {window: 60 * time.Second, tokens: 10},
}

func NewDhtRateLimiter(clock func() time.Time) *DhtRateLimiter {
	if clock == nil {
		clock = time.Now
	}
	return &DhtRateLimiter{
		clock:       clock,
		buckets:     make(map[string]dhtRateBucket),
		lastCleanup: clock(),
	}
}

func (limiter *DhtRateLimiter) Allow(source, packetType string) bool {
	rule, ok := dhtRateRules[packetType]
	if !ok || source == "" {
		return true
	}
	now := limiter.clock()
	limiter.lock.Lock()
	defer limiter.lock.Unlock()
	limiter.cleanupLocked(now)
	key := source + ":" + packetType
	bucket, exists := limiter.buckets[key]
	if !exists {
		bucket = dhtRateBucket{tokens: rule.tokens, lastRefill: now}
	}
	elapsed := now.Sub(bucket.lastRefill).Seconds()
	if elapsed > 0 {
		refill := elapsed * (rule.tokens / rule.window.Seconds())
		bucket.tokens = minFloat(rule.tokens, bucket.tokens+refill)
		bucket.lastRefill = now
	}
	if bucket.tokens < 1 {
		limiter.limitedCount++
		limiter.buckets[key] = bucket
		return false
	}
	bucket.tokens--
	limiter.buckets[key] = bucket
	return true
}

func (limiter *DhtRateLimiter) LimitedCount() int64 {
	limiter.lock.Lock()
	defer limiter.lock.Unlock()
	return limiter.limitedCount
}

func (limiter *DhtRateLimiter) cleanupLocked(now time.Time) {
	if now.Sub(limiter.lastCleanup) < time.Hour {
		return
	}
	limiter.lastCleanup = now
	for key, bucket := range limiter.buckets {
		if now.Sub(bucket.lastRefill) > time.Hour {
			delete(limiter.buckets, key)
		}
	}
}

func minFloat(left, right float64) float64 {
	if left < right {
		return left
	}
	return right
}

type pendingQueryEntry struct {
	expectedType  string
	sourceAddress string
	createdAt     time.Time
}

type PendingQueries struct {
	clock   func() time.Time
	lock    sync.Mutex
	entries map[string]pendingQueryEntry
}

var expectedResponses = map[string]string{
	"DHT_PING":       "DHT_PONG",
	"DHT_FIND_NODE":  "DHT_FOUND_NODES",
	"DHT_FIND_VALUE": "DHT_FOUND_VALUE",
	"DHT_STORE":      "DHT_STORE_ACK",
	"DHT_QUERY":      "DHT_RESPONSE",
}

var responseTypes = map[string]struct{}{
	"DHT_PONG":        {},
	"DHT_FOUND_NODES": {},
	"DHT_FOUND_VALUE": {},
	"DHT_STORE_ACK":   {},
	"DHT_RESPONSE":    {},
}

func NewPendingQueries(clock func() time.Time) *PendingQueries {
	if clock == nil {
		clock = time.Now
	}
	return &PendingQueries{
		clock:   clock,
		entries: make(map[string]pendingQueryEntry),
	}
}

func (pending *PendingQueries) Register(queryID, requestType, sourceAddress string) bool {
	if !validQueryID(queryID) {
		return false
	}
	expectedType, ok := expectedResponses[requestType]
	if !ok {
		return false
	}
	now := pending.clock()
	pending.lock.Lock()
	defer pending.lock.Unlock()
	pending.cleanupLocked(now)
	if _, exists := pending.entries[queryID]; exists {
		return false
	}
	pending.entries[queryID] = pendingQueryEntry{
		expectedType: expectedType, sourceAddress: sourceAddress, createdAt: now,
	}
	return true
}

func (pending *PendingQueries) Consume(queryID, responseType, sourceAddress string) bool {
	now := pending.clock()
	pending.lock.Lock()
	defer pending.lock.Unlock()
	pending.cleanupLocked(now)
	entry, ok := pending.entries[queryID]
	if !ok || entry.expectedType != responseType || entry.sourceAddress != sourceAddress {
		return false
	}
	delete(pending.entries, queryID)
	return true
}

func (pending *PendingQueries) Size() int {
	now := pending.clock()
	pending.lock.Lock()
	defer pending.lock.Unlock()
	pending.cleanupLocked(now)
	return len(pending.entries)
}

func (pending *PendingQueries) cleanupLocked(now time.Time) {
	for queryID, entry := range pending.entries {
		if now.Sub(entry.createdAt) > MaxPendingQueryAgeNanos {
			delete(pending.entries, queryID)
		}
	}
}

func validQueryID(value string) bool {
	return value != "" && len(value) <= MaxQueryIDLength
}

func IsResponseType(packetType string) bool {
	_, ok := responseTypes[packetType]
	return ok
}

func CorrelationKey(packet DecodedPacket) string {
	if response, ok := packet.Message.(*DhtResponse); ok && response.QueryID == "" {
		return response.TargetID
	}
	return packet.QueryID
}

type AdmissionPolicy struct {
	limiter *DhtRateLimiter
	pending *PendingQueries
}

func NewAdmissionPolicy(clock func() time.Time) *AdmissionPolicy {
	return &AdmissionPolicy{
		limiter: NewDhtRateLimiter(clock),
		pending: NewPendingQueries(clock),
	}
}

func (policy *AdmissionPolicy) RegisterQuery(queryID, requestType, sourceAddress string) bool {
	return policy.pending.Register(queryID, requestType, sourceAddress)
}

func (policy *AdmissionPolicy) Admit(packetJSON, sourceAddress, publicKeyHex, expectedUpeerID string) (DecodedPacket, error) {
	packet, err := decodePacketObject(packetJSON)
	if err != nil {
		return DecodedPacket{}, err
	}
	packetType, err := packetType(packet)
	if err != nil {
		return DecodedPacket{}, err
	}
	if !policy.limiter.Allow("ip:"+sourceAddress, packetType) {
		return DecodedPacket{}, fmt.Errorf("rate limited by source")
	}
	metadataJSON, err := VerifySignedPacket(packetJSON, publicKeyHex)
	if err != nil {
		return DecodedPacket{}, fmt.Errorf("invalid signature")
	}
	var metadata map[string]any
	if err := json.Unmarshal([]byte(metadataJSON), &metadata); err != nil {
		return DecodedPacket{}, fmt.Errorf("invalid signature")
	}
	senderUpeerID, ok := metadata["senderUpeerId"].(string)
	if !ok || senderUpeerID == "" {
		return DecodedPacket{}, fmt.Errorf("invalid signature")
	}
	if !strings.EqualFold(senderUpeerID, expectedUpeerID) {
		return DecodedPacket{}, fmt.Errorf("senderUpeerId mismatch")
	}
	if !policy.limiter.Allow("id:"+strings.ToLower(senderUpeerID), packetType) {
		return DecodedPacket{}, fmt.Errorf("rate limited by identity")
	}
	decoded, err := DecodePacketObject(packet)
	if err != nil {
		return DecodedPacket{}, err
	}
	if IsResponseType(decoded.Type) {
		key := CorrelationKey(decoded)
		if key == "" || !policy.pending.Consume(key, decoded.Type, sourceAddress) {
			return DecodedPacket{}, fmt.Errorf("missing pending query")
		}
	}
	return decoded, nil
}
