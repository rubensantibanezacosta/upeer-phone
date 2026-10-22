package dht

import (
	"fmt"
	"strings"
	"testing"
	"time"
)

func TestAdmissionAcceptsSignedDesktopRequests(t *testing.T) {
	fixture := loadCodecFixture(t)
	policy := NewAdmissionPolicy(time.Now)
	names := []string{
		"DHT_PING",
		"DHT_FIND_NODE",
		"DHT_FIND_VALUE",
		"DHT_STORE",
		"DHT_QUERY",
		"DHT_UPDATE",
		"DHT_EXCHANGE",
	}
	for _, name := range names {
		packet := packetNamed(t, fixture, name)
		decoded, err := policy.Admit(
			marshalPacket(t, packet),
			fixture.Identity.Address,
			fixture.Identity.PublicKey,
			fixture.Identity.UpeerID,
		)
		if err != nil {
			t.Fatalf("%s: %v", name, err)
		}
		if decoded.Type != name {
			t.Fatalf("%s decoded as %s", name, decoded.Type)
		}
	}
}

func TestAdmissionRejectsResponsesWithoutPendingQuery(t *testing.T) {
	fixture := loadCodecFixture(t)
	policy := NewAdmissionPolicy(time.Now)
	_, err := policy.Admit(
		marshalPacket(t, packetNamed(t, fixture, "DHT_FOUND_VALUE")),
		fixture.Identity.Address,
		fixture.Identity.PublicKey,
		fixture.Identity.UpeerID,
	)
	expectCodecError(t, "missing pending query", func() error { return err })
}

func TestAdmissionAcceptsResponsesWithPendingQuery(t *testing.T) {
	fixture := loadCodecFixture(t)
	policy := NewAdmissionPolicy(time.Now)
	packet := packetNamed(t, fixture, "DHT_FOUND_VALUE")
	queryID, _ := packet["queryId"].(string)
	if !policy.RegisterQuery(queryID, "DHT_FIND_VALUE", fixture.Identity.Address) {
		t.Fatal("query was not registered")
	}
	decoded, err := policy.Admit(
		marshalPacket(t, packet),
		fixture.Identity.Address,
		fixture.Identity.PublicKey,
		fixture.Identity.UpeerID,
	)
	if err != nil {
		t.Fatal(err)
	}
	if decoded.Type != "DHT_FOUND_VALUE" {
		t.Fatalf("unexpected type %s", decoded.Type)
	}
}

func TestAdmissionCorrelatesLegacyResponseByTarget(t *testing.T) {
	fixture := loadCodecFixture(t)
	policy := NewAdmissionPolicy(time.Now)
	packet := packetNamed(t, fixture, "DHT_RESPONSE")
	targetID, _ := packet["targetId"].(string)
	if !policy.RegisterQuery(targetID, "DHT_QUERY", fixture.Identity.Address) {
		t.Fatal("legacy query was not registered")
	}
	if _, err := policy.Admit(
		marshalPacket(t, packet),
		fixture.Identity.Address,
		fixture.Identity.PublicKey,
		fixture.Identity.UpeerID,
	); err != nil {
		t.Fatal(err)
	}
}

func TestAdmissionRejectsSenderMismatch(t *testing.T) {
	fixture := loadCodecFixture(t)
	policy := NewAdmissionPolicy(time.Now)
	_, err := policy.Admit(
		marshalPacket(t, packetNamed(t, fixture, "DHT_PING")),
		fixture.Identity.Address,
		fixture.Identity.PublicKey,
		strings.Repeat("a", 64),
	)
	expectCodecError(t, "senderUpeerId mismatch", func() error { return err })
}

func TestAdmissionRateLimitsBySourceAndIdentity(t *testing.T) {
	fixture := loadCodecFixture(t)
	now := time.Unix(1_700_000_000, 0)
	packetJSON := marshalPacket(t, packetNamed(t, fixture, "DHT_FIND_NODE"))

	bySource := NewAdmissionPolicy(func() time.Time { return now })
	for index := 0; index < 30; index++ {
		if _, err := bySource.Admit(
			packetJSON, "200::1", fixture.Identity.PublicKey, fixture.Identity.UpeerID,
		); err != nil {
			t.Fatalf("request %d: %v", index, err)
		}
	}
	_, err := bySource.Admit(packetJSON, "200::1", fixture.Identity.PublicKey, fixture.Identity.UpeerID)
	expectCodecError(t, "rate limited by source", func() error { return err })

	byIdentity := NewAdmissionPolicy(func() time.Time { return now })
	for index := 0; index < 30; index++ {
		source := fmt.Sprintf("200::%d", index+1)
		if _, err := byIdentity.Admit(
			packetJSON, source, fixture.Identity.PublicKey, fixture.Identity.UpeerID,
		); err != nil {
			t.Fatalf("request %d: %v", index, err)
		}
	}
	_, err = byIdentity.Admit(packetJSON, "200::31", fixture.Identity.PublicKey, fixture.Identity.UpeerID)
	expectCodecError(t, "rate limited by identity", func() error { return err })
	if bySource.limiter.LimitedCount() != 1 {
		t.Fatalf("unexpected limited count %d", bySource.limiter.LimitedCount())
	}
}

func packetNamed(t *testing.T, fixture codecFixture, name string) map[string]any {
	t.Helper()
	for _, vector := range fixture.SignedPackets {
		if vector.Name == name {
			return vector.Packet
		}
	}
	t.Fatalf("vector %s is missing", name)
	return nil
}
