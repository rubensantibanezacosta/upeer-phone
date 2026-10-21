package transportbridge

import (
	"errors"
	"net"
)

func validateEndpoint(address string, port int) error {
	ip := net.ParseIP(address)
	if ip == nil || ip.To4() != nil || !isYggdrasilAddress(ip) {
		return errors.New("destination must be a Yggdrasil IPv6 address")
	}
	if port < 1 || port > 65535 {
		return errors.New("destination port must be between 1 and 65535")
	}
	return nil
}

func isYggdrasilAddress(ip net.IP) bool {
	_, subnet, err := net.ParseCIDR("0200::/7")
	return err == nil && subnet.Contains(ip)
}
