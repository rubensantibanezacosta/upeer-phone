package dht

import (
	"errors"
	"strings"
	"time"
)

func (node *KademliaNode) dispatchQueries(
	contacts []KademliaContact,
	target string,
	build func(queryID string) any,
) ([]string, error) {
	queryIDs := make([]string, 0, len(contacts))
	var firstErr error
	for _, contact := range contacts {
		queryID := newKademliaQueryID()
		if queryID == "" {
			if firstErr == nil {
				firstErr = errors.New("generate Kademlia query identifier")
			}
			continue
		}
		if !node.queries.Register(queryID, target) {
			continue
		}
		if err := node.sendMessage(contact.Address, build(queryID)); err != nil {
			node.queries.Cancel(queryID)
			if firstErr == nil {
				firstErr = err
			}
			continue
		}
		queryIDs = append(queryIDs, queryID)
	}
	return queryIDs, firstErr
}

func (node *KademliaNode) collectResponses(queryIDs []string) []any {
	if len(queryIDs) == 0 {
		return nil
	}
	outcomes := make(chan any, len(queryIDs))
	for _, queryID := range queryIDs {
		go func(id string) {
			if message, ok := node.queries.Wait(id, node.queryTimeout); ok {
				outcomes <- message
			}
		}(queryID)
	}
	timer := time.NewTimer(node.queryTimeout)
	defer timer.Stop()
	responses := make([]any, 0, len(queryIDs))
	for len(responses) < len(queryIDs) {
		select {
		case message := <-outcomes:
			responses = append(responses, message)
		case <-timer.C:
			for {
				select {
				case message := <-outcomes:
					responses = append(responses, message)
				default:
					return responses
				}
			}
		}
	}
	return responses
}

func (node *KademliaNode) ProbeContacts(candidates []KademliaContact, timeout time.Duration) []KademliaContact {
	if timeout <= 0 {
		timeout = node.queryTimeout
	}
	type pendingProbe struct {
		queryID string
		contact KademliaContact
	}
	pending := make([]pendingProbe, 0, len(candidates))
	seen := make(map[string]struct{})
	for _, candidate := range candidates {
		upeerID := strings.ToLower(strings.TrimSpace(candidate.UpeerID))
		if upeerID == "" || upeerID == node.upeerID || candidate.Address == "" {
			continue
		}
		if _, exists := seen[upeerID]; exists {
			continue
		}
		seen[upeerID] = struct{}{}
		queryID := newKademliaQueryID()
		if !node.queries.Register(queryID, "") {
			continue
		}
		if err := node.sendMessage(candidate.Address, &DhtPing{NodeID: node.nodeID.Hex()}); err != nil {
			node.queries.Cancel(queryID)
			continue
		}
		if err := node.sendMessage(candidate.Address, &DhtFindNode{QueryID: queryID, TargetID: node.upeerID}); err != nil {
			node.queries.Cancel(queryID)
			continue
		}
		contact := candidate
		contact.UpeerID = upeerID
		contact.LastSeen = node.clock()
		pending = append(pending, pendingProbe{queryID: queryID, contact: contact})
	}
	if len(pending) == 0 {
		return nil
	}

	type probeOutcome struct {
		index   int
		message any
		ok      bool
	}
	outcomes := make(chan probeOutcome, len(pending))
	for index, probe := range pending {
		go func(position int, queryID string) {
			message, ok := node.queries.Wait(queryID, timeout)
			outcomes <- probeOutcome{index: position, message: message, ok: ok}
		}(index, probe.queryID)
	}
	timer := time.NewTimer(timeout)
	defer timer.Stop()
	confirmed := make([]bool, len(pending))
	remaining := len(pending)
	timedOut := false
	for remaining > 0 {
		select {
		case outcome := <-outcomes:
			_, isNodes := outcome.message.(*DhtFoundNodes)
			confirmed[outcome.index] = outcome.ok && isNodes
			remaining--
		case <-timer.C:
			remaining = 0
			timedOut = true
		}
	}
	verified := make([]KademliaContact, 0, len(pending))
	for index, probe := range pending {
		if !confirmed[index] {
			continue
		}
		node.routing.AddContact(probe.contact)
		verified = append(verified, probe.contact)
	}
	if timedOut {
		for _, probe := range pending {
			node.queries.Cancel(probe.queryID)
		}
	}
	return verified
}
