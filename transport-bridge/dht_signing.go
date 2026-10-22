package transportbridge

import (
	"encoding/json"
	"errors"
	"fmt"
	"time"

	"github.com/upeer/upeer/transport-bridge/dht"
)

func (identity *CryptoIdentity) SignCanonicalJSON(payloadJSON string) (string, error) {
	return dht.SignCanonicalJSON(identity.secretKey, payloadJSON)
}

func (identity *CryptoIdentity) BuildSignedPacket(senderYggAddress, packetJSON string) (string, error) {
	return dht.BuildSignedPacket(identity.secretKey, senderYggAddress, packetJSON)
}

func BuildSignedPacket(secretKey []byte, senderYggAddress, packetJSON string) (string, error) {
	return dht.BuildSignedPacket(secretKey, senderYggAddress, packetJSON)
}

func VerifyCanonicalJSON(payloadJSON, signatureHex, publicKeyHex string) (bool, error) {
	return dht.VerifyCanonicalJSON(payloadJSON, signatureHex, publicKeyHex)
}

func VerifySignedPacket(packetJSON, publicKeyHex string) (string, error) {
	return dht.VerifySignedPacket(packetJSON, publicKeyHex)
}

func BuildDevicePairingRequest(deviceSecretKey []byte, optionsJSON string) (string, error) {
	var options dht.DevicePairingRequestOptions
	if err := json.Unmarshal([]byte(optionsJSON), &options); err != nil {
		return "", fmt.Errorf("decode device pairing options: %w", err)
	}
	return dht.BuildDevicePairingRequest(deviceSecretKey, options)
}

func (identity *CryptoIdentity) BuildDevicePairingRequest(optionsJSON string) (string, error) {
	return BuildDevicePairingRequest(identity.secretKey, optionsJSON)
}

func VerifyDevicePairingRequest(requestJSON, expectedAccountUpeerID string, nowMillis int64) (bool, error) {
	_, err := dht.VerifyDevicePairingRequest(requestJSON, expectedAccountUpeerID, nowMillis)
	if err != nil {
		return false, err
	}
	return true, nil
}

func ApproveDevicePairingRequest(accountSecretKey []byte, requestJSON string, epoch, issuedAt, expiresAt int64) (string, error) {
	return dht.ApproveDevicePairingRequest(accountSecretKey, requestJSON, epoch, issuedAt, expiresAt)
}

func (identity *CryptoIdentity) ApproveDevicePairingRequest(requestJSON string, epoch, issuedAt, expiresAt int64) (string, error) {
	return dht.ApproveDevicePairingRequest(identity.secretKey, requestJSON, epoch, issuedAt, expiresAt)
}

func VerifyDeviceCertificate(certificateJSON, expectedAccountUpeerID string, nowMillis int64) (bool, error) {
	_, err := dht.VerifyDeviceCertificate(certificateJSON, expectedAccountUpeerID, nowMillis)
	if err != nil {
		return false, err
	}
	return true, nil
}

func BuildAccountRegistryFromCertificates(accountSecretKey []byte, certificatesJSON, revokedJSON string, epoch, issuedAt, ttlMillis int64) (string, error) {
	identity, err := NewCryptoIdentity(accountSecretKey)
	if err != nil {
		return "", err
	}
	var encodedCertificates []string
	if err := json.Unmarshal([]byte(certificatesJSON), &encodedCertificates); err != nil {
		return "", fmt.Errorf("decode account registry certificates: %w", err)
	}
	now := issuedAt
	if now <= 0 {
		now = time.Now().UnixMilli()
	}
	devices := make([]dht.AccountRegistryDevice, 0, len(encodedCertificates))
	for _, encoded := range encodedCertificates {
		certificate, err := dht.VerifyDeviceCertificate(encoded, identity.UpeerID(), now)
		if err != nil {
			return "", err
		}
		devices = append(devices, dht.AccountRegistryDeviceFromCertificate(certificate))
	}
	var revoked []dht.AccountRevocation
	if revokedJSON != "" {
		if err := json.Unmarshal([]byte(revokedJSON), &revoked); err != nil {
			return "", fmt.Errorf("decode account registry revocations: %w", err)
		}
	}
	return dht.BuildAccountRegistry(accountSecretKey, dht.AccountRegistryOptions{
		Epoch:     epoch,
		IssuedAt:  issuedAt,
		TTLMillis: ttlMillis,
		NowMillis: now,
		Devices:   devices,
		Revoked:   revoked,
	})
}

func (identity *CryptoIdentity) BuildAccountRegistryFromCertificates(certificatesJSON, revokedJSON string, epoch, issuedAt, ttlMillis int64) (string, error) {
	return BuildAccountRegistryFromCertificates(identity.secretKey, certificatesJSON, revokedJSON, epoch, issuedAt, ttlMillis)
}

func VerifyAccountRegistry(registryJSON, expectedAccountUpeerID string, nowMillis int64) (bool, error) {
	_, err := dht.VerifyAccountRegistry(registryJSON, expectedAccountUpeerID, nowMillis)
	if err != nil {
		return false, err
	}
	return true, nil
}

func ValidateDeviceAdmission(certificateJSON, registryJSON, expectedAccountUpeerID string, nowMillis int64) (bool, error) {
	if certificateJSON == "" || registryJSON == "" {
		return false, errors.New("device admission requires a certificate and an account registry")
	}
	_, err := dht.ValidateDeviceAdmission(certificateJSON, registryJSON, expectedAccountUpeerID, nowMillis)
	if err != nil {
		return false, err
	}
	return true, nil
}

func ValidateAccountRegistryUpdate(previousJSON, nextJSON, expectedAccountUpeerID string, nowMillis int64) (bool, error) {
	_, err := dht.ValidateAccountRegistryUpdate(previousJSON, nextJSON, expectedAccountUpeerID, nowMillis)
	if err != nil {
		return false, err
	}
	return true, nil
}

func RevokeAccountDevice(previousRegistryJSON string, accountSecretKey []byte, deviceID, reason string, nowMillis int64) (string, error) {
	return dht.RevokeAccountDevice(previousRegistryJSON, accountSecretKey, deviceID, reason, nowMillis)
}

func (identity *CryptoIdentity) RevokeAccountDevice(previousRegistryJSON, deviceID, reason string, nowMillis int64) (string, error) {
	return dht.RevokeAccountDevice(previousRegistryJSON, identity.secretKey, deviceID, reason, nowMillis)
}

func AccountRegistryKey(accountUpeerID string) (string, error) {
	return dht.AccountRegistryKey(accountUpeerID)
}

func NextAccountRegistryEpoch(registryJSON string) (int64, error) {
	return dht.NextAccountRegistryEpoch(registryJSON)
}

func BuildDeviceLocationBlock(deviceSecretKey []byte, certificateJSON, addressesJSON string, dhtSeq, ttlMillis, nowMillis int64) (string, error) {
	var addresses []string
	if err := json.Unmarshal([]byte(addressesJSON), &addresses); err != nil {
		return "", fmt.Errorf("decode device location addresses: %w", err)
	}
	return dht.BuildDeviceLocationBlock(deviceSecretKey, certificateJSON, dht.LocationBlockOptions{
		Addresses: addresses,
		DhtSeq:    dhtSeq,
		TTLMillis: ttlMillis,
		NowMillis: nowMillis,
	})
}

func (identity *CryptoIdentity) BuildDeviceLocationBlock(certificateJSON, addressesJSON string, dhtSeq, ttlMillis, nowMillis int64) (string, error) {
	return BuildDeviceLocationBlock(identity.secretKey, certificateJSON, addressesJSON, dhtSeq, ttlMillis, nowMillis)
}

func VerifyDeviceLocationBlock(blockJSON, accountPublicKeyHex, registryJSON string, nowMillis int64) (bool, error) {
	return dht.VerifyDeviceLocationBlock(blockJSON, accountPublicKeyHex, registryJSON, nowMillis)
}
