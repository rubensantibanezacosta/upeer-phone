package dht

import (
	"errors"
	"fmt"
	"strings"
)

func ValidateDeviceAdmission(certificateJSON, registryJSON, expectedAccountUpeerID string, nowMillis int64) (AccountDeviceCertificate, error) {
	certificate, err := VerifyDeviceCertificate(certificateJSON, expectedAccountUpeerID, nowMillis)
	if err != nil {
		return AccountDeviceCertificate{}, err
	}
	if strings.TrimSpace(registryJSON) == "" {
		return AccountDeviceCertificate{}, errors.New("device admission requires an account registry")
	}
	registry, err := VerifyAccountRegistry(registryJSON, certificate.AccountUpeerID, nowMillis)
	if err != nil {
		return AccountDeviceCertificate{}, err
	}
	if !strings.EqualFold(registry.AccountPublicKey, certificate.AccountPublicKey) {
		return AccountDeviceCertificate{}, errors.New("device certificate and account registry use different account keys")
	}
	if certificate.Epoch > registry.Epoch {
		return AccountDeviceCertificate{}, errors.New("device certificate epoch is ahead of the account registry")
	}
	if certificate.IssuedAt > registry.IssuedAt {
		return AccountDeviceCertificate{}, errors.New("device certificate was issued after the current account registry")
	}
	for _, revocation := range registry.Revoked {
		if strings.EqualFold(revocation.DeviceID, certificate.DeviceID) {
			return AccountDeviceCertificate{}, errors.New("device is revoked in the current account registry")
		}
	}
	device, ok := findAccountRegistryDevice(registry, certificate.DeviceID)
	if !ok {
		return AccountDeviceCertificate{}, errors.New("device is not authorized in the current account registry")
	}
	if !strings.EqualFold(device.DevicePublicKey, certificate.DevicePublicKey) {
		return AccountDeviceCertificate{}, errors.New("account registry device public key does not match the certificate")
	}
	if !strings.EqualFold(device.StaticX25519PublicKey, certificate.StaticX25519PublicKey) {
		return AccountDeviceCertificate{}, errors.New("account registry static key does not match the certificate")
	}
	if device.Platform != certificate.Platform {
		return AccountDeviceCertificate{}, errors.New("account registry platform does not match the certificate")
	}
	if !equalAccountStrings(device.Capabilities, certificate.Capabilities) {
		return AccountDeviceCertificate{}, errors.New("account registry capabilities do not match the certificate")
	}
	if device.AuthorizedAt != certificate.IssuedAt || device.ExpiresAt != certificate.ExpiresAt {
		return AccountDeviceCertificate{}, errors.New("account registry validity does not match the certificate")
	}
	if device.Epoch != certificate.Epoch {
		return AccountDeviceCertificate{}, fmt.Errorf("account registry epoch %d does not match certificate epoch %d", device.Epoch, certificate.Epoch)
	}
	return certificate, nil
}

func findAccountRegistryDevice(registry AccountRegistry, deviceID string) (AccountRegistryDevice, bool) {
	for _, device := range registry.Devices {
		if strings.EqualFold(device.DeviceID, deviceID) {
			return device, true
		}
	}
	return AccountRegistryDevice{}, false
}
