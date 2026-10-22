package dht

import (
	"fmt"
	"sync"
	"time"
)

const KademliaLanMulticastAddress = "ff02::1"

type KademliaLanPolicy struct {
	MulticastPermitted      bool
	WifiMulticastPermission bool
	ServiceActive           bool
	NetworkAvailable        bool
	BatterySaving           bool
}

func (policy KademliaLanPolicy) Allowed() bool {
	return policy.MulticastPermitted && policy.WifiMulticastPermission &&
		policy.ServiceActive && policy.NetworkAvailable && !policy.BatterySaving
}

type KademliaLanDiscovery struct {
	node          *KademliaNode
	policy        func() KademliaLanPolicy
	clock         func() time.Time
	probeInterval time.Duration
	lock          sync.Mutex
	lastProbe     time.Time
}

func NewKademliaLanDiscovery(
	node *KademliaNode,
	policy func() KademliaLanPolicy,
	clock func() time.Time,
	probeInterval time.Duration,
) *KademliaLanDiscovery {
	if clock == nil {
		clock = time.Now
	}
	if probeInterval <= 0 {
		probeInterval = KademliaLanProbeInterval
	}
	return &KademliaLanDiscovery{
		node: node, policy: policy, clock: clock, probeInterval: probeInterval,
	}
}

func (discovery *KademliaLanDiscovery) Probe(scopeID int) bool {
	if discovery.policy == nil || !discovery.policy().Allowed() {
		return false
	}
	now := discovery.clock()
	discovery.lock.Lock()
	tooSoon := !discovery.lastProbe.IsZero() && now.Sub(discovery.lastProbe) < discovery.probeInterval
	discovery.lock.Unlock()
	if tooSoon {
		return false
	}
	if !discovery.node.BroadcastPing(KademliaLanDestination(scopeID)) {
		return false
	}
	discovery.lock.Lock()
	discovery.lastProbe = now
	discovery.lock.Unlock()
	return true
}

func (discovery *KademliaLanDiscovery) LastProbe() time.Time {
	discovery.lock.Lock()
	defer discovery.lock.Unlock()
	return discovery.lastProbe
}

func KademliaLanDestination(scopeID int) string {
	if scopeID > 0 {
		return fmt.Sprintf("%s%%%d", KademliaLanMulticastAddress, scopeID)
	}
	return KademliaLanMulticastAddress
}
