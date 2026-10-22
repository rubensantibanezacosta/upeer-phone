package dht

import (
	"sync"
	"time"
)

type KademliaBootstrapStats struct {
	Attempts          int
	Successes         int
	Failures          int
	SeedsLoaded       int
	SeedsRejected     int
	SeedsDuplicate    int
	SeedSourceFailure int
	VerifiedContacts  int
	ContactCount      int
	Bootstrapped      bool
	LastAttempt       time.Time
	LastSuccess       time.Time
	NextAttempt       time.Time
	PersistFailures   int64
}

type KademliaBootstrapResult struct {
	Ran              bool
	Bootstrapped     bool
	VerifiedContacts int
	ContactCount     int
	SeedsLoaded      int
	SeedsRejected    int
	NextAttempt      time.Time
}

type KademliaBootstrapConfig struct {
	Clock        func() time.Time
	MinNodes     int
	Retry        time.Duration
	MaxRetry     time.Duration
	ProbeTimeout time.Duration
	LookupRounds int
	ProbeBatch   int
}

type KademliaBootstrap struct {
	node         *KademliaNode
	loader       *KademliaSeedLoader
	store        KademliaContactStore
	clock        func() time.Time
	minNodes     int
	retry        time.Duration
	maxRetry     time.Duration
	probeTimeout time.Duration
	lookupRounds int
	probeBatch   int

	attemptLock         sync.Mutex
	lock                sync.Mutex
	verified            map[string]struct{}
	attempts            int
	successes           int
	failures            int
	seedsLoaded         int
	seedsRejected       int
	seedsDuplicate      int
	seedSourceFailures  int
	lastAttempt         time.Time
	lastSuccess         time.Time
	nextAttempt         time.Time
	consecutiveFailures int
	persistFailures     int64
	bootstrapped        bool
}

func NewKademliaBootstrap(
	node *KademliaNode,
	loader *KademliaSeedLoader,
	store KademliaContactStore,
	config KademliaBootstrapConfig,
) *KademliaBootstrap {
	if config.Clock == nil {
		config.Clock = time.Now
	}
	if config.MinNodes <= 0 {
		config.MinNodes = KademliaBootstrapMinNodes
	}
	if config.Retry <= 0 {
		config.Retry = KademliaBootstrapRetry
	}
	if config.MaxRetry < config.Retry {
		config.MaxRetry = KademliaBootstrapMaxRetry
		if config.MaxRetry < config.Retry {
			config.MaxRetry = config.Retry
		}
	}
	if config.ProbeTimeout <= 0 {
		config.ProbeTimeout = KademliaQueryTimeout
	}
	if config.LookupRounds < 0 {
		config.LookupRounds = 0
	} else if config.LookupRounds == 0 {
		config.LookupRounds = KademliaBootstrapLookupRounds
	}
	if config.ProbeBatch <= 0 {
		config.ProbeBatch = KademliaBootstrapProbeBatch
	}
	bootstrap := &KademliaBootstrap{
		node: node, loader: loader, store: store, clock: config.Clock,
		minNodes: config.MinNodes, retry: config.Retry, maxRetry: config.MaxRetry,
		probeTimeout: config.ProbeTimeout, lookupRounds: config.LookupRounds,
		probeBatch: config.ProbeBatch, verified: make(map[string]struct{}),
	}
	if store != nil {
		contacts, err := store.Load()
		if err != nil {
			bootstrap.persistFailures++
		} else if len(contacts) > 0 {
			bootstrap.BootstrapFromContacts(contacts)
		}
	}
	bootstrap.lock.Lock()
	bootstrap.updateStatusLocked()
	bootstrap.lock.Unlock()
	return bootstrap
}

func (bootstrap *KademliaBootstrap) BootstrapFromContacts(contacts []KademliaContact) int {
	added := 0
	bootstrap.lock.Lock()
	defer bootstrap.lock.Unlock()
	for _, contact := range contacts {
		normalized, ok := NormalizeKademliaContact(contact, bootstrap.node.upeerID)
		if !ok {
			continue
		}
		if bootstrap.node.AddContact(normalized) {
			added++
		}
		bootstrap.verified[normalized.UpeerID] = struct{}{}
	}
	bootstrap.updateStatusLocked()
	return added
}

func (bootstrap *KademliaBootstrap) Attempt() KademliaBootstrapResult {
	bootstrap.attemptLock.Lock()
	defer bootstrap.attemptLock.Unlock()
	startedAt := bootstrap.clock()
	bootstrap.lock.Lock()
	bootstrap.updateStatusLocked()
	alreadyBootstrapped := bootstrap.bootstrapped
	bootstrap.lock.Unlock()
	if alreadyBootstrapped {
		return bootstrap.result(false, 0)
	}

	load := bootstrap.loader.Load()
	bootstrap.lock.Lock()
	bootstrap.attempts++
	bootstrap.lastAttempt = startedAt
	bootstrap.seedsLoaded = len(load.Seeds)
	bootstrap.seedsRejected += load.Rejected
	bootstrap.seedsDuplicate += load.Duplicates
	bootstrap.seedSourceFailures += load.SourceFailures
	bootstrap.lock.Unlock()

	seedContacts := make([]KademliaContact, 0, len(load.Seeds))
	for _, seed := range load.Seeds {
		nodeID, err := NewKademliaID(seed.UpeerID)
		if err != nil {
			continue
		}
		seedContacts = append(seedContacts, KademliaContact{
			NodeID: nodeID, UpeerID: seed.UpeerID, Address: seed.Address,
			PublicKey: seed.PublicKey, LastSeen: startedAt,
		})
	}
	bootstrap.markVerified(bootstrap.node.ProbeContacts(seedContacts, bootstrap.probeTimeout))

	for round := 0; round < bootstrap.lookupRounds; round++ {
		if bootstrap.currentVerifiedCount() >= bootstrap.minNodes {
			break
		}
		before := bootstrap.node.routing.ContactCount()
		_, _ = bootstrap.node.FindNode(bootstrap.node.upeerID)
		if bootstrap.node.routing.ContactCount() <= before {
			break
		}
	}

	attempted := make(map[string]struct{})
	for bootstrap.currentVerifiedCount() < bootstrap.minNodes {
		candidates := bootstrap.unverifiedCandidates(attempted)
		if len(candidates) == 0 {
			break
		}
		bootstrap.markVerified(bootstrap.node.ProbeContacts(candidates, bootstrap.probeTimeout))
	}
	finishedAt := bootstrap.clock()
	bootstrap.lock.Lock()
	bootstrap.updateStatusLocked()
	if bootstrap.bootstrapped {
		bootstrap.successes++
		bootstrap.lastSuccess = finishedAt
		bootstrap.consecutiveFailures = 0
		bootstrap.nextAttempt = finishedAt.Add(bootstrap.retry)
	} else {
		bootstrap.failures++
		bootstrap.consecutiveFailures++
		bootstrap.nextAttempt = finishedAt.Add(bootstrap.backoffLocked())
	}
	result := bootstrap.resultLocked(true, len(load.Seeds))
	bootstrap.lock.Unlock()
	bootstrap.persistVerifiedContacts()
	return result
}
