package transportbridge

import (
	"crypto/ed25519"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"net"

	"github.com/yggdrasil-network/yggdrasil-go/src/config"
	"github.com/yggdrasil-network/yggdrasil-go/src/core"
)

func startConfiguredNode(configJSON []byte) (*core.Core, error) {
	cfg := config.GenerateConfig()
	if err := cfg.UnmarshalHJSON(configJSON); err != nil {
		return nil, fmt.Errorf("parse Yggdrasil configuration: %w", err)
	}
	if len(cfg.PrivateKey) != ed25519.PrivateKeySize {
		return nil, errors.New("Yggdrasil identity private key is missing or invalid")
	}
	ipRange := &net.IPNet{IP: net.ParseIP("200::"), Mask: net.CIDRMask(7, 128)}
	options := []core.SetupOption{
		core.PeerFilter(func(ip net.IP) bool { return !ipRange.Contains(ip) }),
		core.NodeInfo(cfg.NodeInfo),
		core.NodeInfoPrivacy(cfg.NodeInfoPrivacy),
		core.GroupPassword(cfg.GroupPassword),
	}
	for _, peer := range cfg.Peers {
		options = append(options, core.Peer{URI: peer})
	}
	for iface, peers := range cfg.InterfacePeers {
		for _, peer := range peers {
			options = append(options, core.Peer{URI: peer, SourceInterface: iface})
		}
	}
	for _, key := range cfg.AllowedPublicKeys {
		decoded, err := hex.DecodeString(key)
		if err != nil {
			return nil, fmt.Errorf("decode allowed public key: %w", err)
		}
		options = append(options, core.AllowedPublicKey(decoded))
	}
	for _, listen := range cfg.Listen {
		options = append(options, core.ListenAddress(listen))
	}
	node, err := core.New(cfg.Certificate, nil, options...)
	if err != nil {
		return nil, fmt.Errorf("start Yggdrasil core: %w", err)
	}
	return node, nil
}

func DefaultConfigJSON() ([]byte, error) {
	cfg := config.GenerateConfig()
	cfg.IfName = "none"
	encoded, err := json.Marshal(cfg)
	if err != nil {
		return nil, fmt.Errorf("encode default Yggdrasil config: %w", err)
	}
	return encoded, nil
}
