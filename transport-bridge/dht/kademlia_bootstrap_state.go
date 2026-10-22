package dht

import (
	"strings"
	"time"
)

func (bootstrap *KademliaBootstrap) Maintenance() (KademliaBootstrapResult, bool) {
	bootstrap.lock.Lock()
	bootstrap.updateStatusLocked()
	shouldAttempt := !bootstrap.bootstrapped && !bootstrap.clock().Before(bootstrap.nextAttempt)
	bootstrap.lock.Unlock()
	if !shouldAttempt {
		return KademliaBootstrapResult{}, false
	}
	return bootstrap.Attempt(), true
}

func (bootstrap *KademliaBootstrap) RetryNow() KademliaBootstrapResult {
	bootstrap.lock.Lock()
	bootstrap.nextAttempt = time.Time{}
	bootstrap.consecutiveFailures = 0
	bootstrap.lock.Unlock()
	return bootstrap.Attempt()
}

func (bootstrap *KademliaBootstrap) OnNetworkChanged() {
	bootstrap.lock.Lock()
	bootstrap.nextAttempt = time.Time{}
	bootstrap.consecutiveFailures = 0
	bootstrap.lock.Unlock()
}

func (bootstrap *KademliaBootstrap) IsBootstrapped() bool {
	bootstrap.lock.Lock()
	defer bootstrap.lock.Unlock()
	bootstrap.updateStatusLocked()
	return bootstrap.bootstrapped
}

func (bootstrap *KademliaBootstrap) VerifiedContactCount() int {
	bootstrap.lock.Lock()
	defer bootstrap.lock.Unlock()
	bootstrap.updateStatusLocked()
	return len(bootstrap.verifiedContactsLocked())
}

func (bootstrap *KademliaBootstrap) ContactCount() int {
	return bootstrap.node.routing.ContactCount()
}

func (bootstrap *KademliaBootstrap) Stats() KademliaBootstrapStats {
	bootstrap.lock.Lock()
	defer bootstrap.lock.Unlock()
	bootstrap.updateStatusLocked()
	return KademliaBootstrapStats{
		Attempts: bootstrap.attempts, Successes: bootstrap.successes, Failures: bootstrap.failures,
		SeedsLoaded: bootstrap.seedsLoaded, SeedsRejected: bootstrap.seedsRejected,
		SeedsDuplicate: bootstrap.seedsDuplicate, SeedSourceFailure: bootstrap.seedSourceFailures,
		VerifiedContacts: len(bootstrap.verifiedContactsLocked()), ContactCount: bootstrap.node.routing.ContactCount(),
		Bootstrapped: bootstrap.bootstrapped, LastAttempt: bootstrap.lastAttempt,
		LastSuccess: bootstrap.lastSuccess, NextAttempt: bootstrap.nextAttempt,
		PersistFailures: bootstrap.persistFailures,
	}
}

func (bootstrap *KademliaBootstrap) markVerified(contacts []KademliaContact) {
	if len(contacts) == 0 {
		return
	}
	bootstrap.lock.Lock()
	for _, contact := range contacts {
		bootstrap.verified[normalizeUpeerID(contact.UpeerID)] = struct{}{}
	}
	bootstrap.updateStatusLocked()
	bootstrap.lock.Unlock()
}

func (bootstrap *KademliaBootstrap) currentVerifiedCount() int {
	bootstrap.lock.Lock()
	defer bootstrap.lock.Unlock()
	bootstrap.updateStatusLocked()
	return len(bootstrap.verifiedContactsLocked())
}

func (bootstrap *KademliaBootstrap) unverifiedCandidates(attempted map[string]struct{}) []KademliaContact {
	bootstrap.lock.Lock()
	defer bootstrap.lock.Unlock()
	candidates := make([]KademliaContact, 0, bootstrap.probeBatch)
	for _, contact := range bootstrap.node.routing.AllContacts() {
		normalized, ok := NormalizeKademliaContact(contact, bootstrap.node.upeerID)
		if !ok {
			continue
		}
		if _, verified := bootstrap.verified[normalized.UpeerID]; verified {
			continue
		}
		if _, seen := attempted[normalized.UpeerID]; seen {
			continue
		}
		attempted[normalized.UpeerID] = struct{}{}
		candidates = append(candidates, normalized)
		if len(candidates) >= bootstrap.probeBatch {
			break
		}
	}
	return candidates
}

func (bootstrap *KademliaBootstrap) persistVerifiedContacts() {
	if bootstrap.store == nil {
		return
	}
	bootstrap.lock.Lock()
	contacts := bootstrap.verifiedContactsLocked()
	bootstrap.lock.Unlock()
	if len(contacts) == 0 {
		return
	}
	if err := bootstrap.store.Save(contacts); err != nil {
		bootstrap.lock.Lock()
		bootstrap.persistFailures++
		bootstrap.lock.Unlock()
	}
}

func (bootstrap *KademliaBootstrap) verifiedContactsLocked() []KademliaContact {
	present := bootstrap.node.routing.AllContacts()
	presentIDs := make(map[string]struct{}, len(present))
	for _, contact := range present {
		presentIDs[normalizeUpeerID(contact.UpeerID)] = struct{}{}
	}
	for upeerID := range bootstrap.verified {
		if _, exists := presentIDs[upeerID]; !exists {
			delete(bootstrap.verified, upeerID)
		}
	}
	contacts := make([]KademliaContact, 0, len(bootstrap.verified))
	for _, contact := range present {
		if _, verified := bootstrap.verified[normalizeUpeerID(contact.UpeerID)]; verified {
			contacts = append(contacts, contact)
		}
	}
	return contacts
}

func (bootstrap *KademliaBootstrap) updateStatusLocked() {
	bootstrap.bootstrapped = len(bootstrap.verifiedContactsLocked()) >= bootstrap.minNodes
}

func (bootstrap *KademliaBootstrap) backoffLocked() time.Duration {
	delay := bootstrap.retry
	for attempt := 1; attempt < bootstrap.consecutiveFailures && attempt < 5; attempt++ {
		delay *= 2
		if delay >= bootstrap.maxRetry {
			return bootstrap.maxRetry
		}
	}
	if delay > bootstrap.maxRetry {
		return bootstrap.maxRetry
	}
	return delay
}

func (bootstrap *KademliaBootstrap) result(ran bool, loadedSeeds int) KademliaBootstrapResult {
	bootstrap.lock.Lock()
	defer bootstrap.lock.Unlock()
	return bootstrap.resultLocked(ran, loadedSeeds)
}

func (bootstrap *KademliaBootstrap) resultLocked(ran bool, loadedSeeds int) KademliaBootstrapResult {
	bootstrap.updateStatusLocked()
	return KademliaBootstrapResult{
		Ran: ran, Bootstrapped: bootstrap.bootstrapped,
		VerifiedContacts: len(bootstrap.verifiedContactsLocked()),
		ContactCount:     bootstrap.node.routing.ContactCount(), SeedsLoaded: loadedSeeds,
		SeedsRejected: bootstrap.seedsRejected, NextAttempt: bootstrap.nextAttempt,
	}
}

func normalizeUpeerID(value string) string {
	return strings.ToLower(strings.TrimSpace(value))
}
