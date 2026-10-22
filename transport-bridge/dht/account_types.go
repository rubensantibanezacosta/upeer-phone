package dht

const (
	AccountProtocolVersion                      = 1
	AccountCertificateDefaultTTLMillis    int64 = 90 * 24 * 60 * 60 * 1000
	AccountCertificateMaxTTLMillis        int64 = 365 * 24 * 60 * 60 * 1000
	AccountRegistryDefaultTTLMillis       int64 = 24 * 60 * 60 * 1000
	AccountRegistryMaxTTLMillis           int64 = 7 * 24 * 60 * 60 * 1000
	AccountPairingRequestDefaultTTLMillis int64 = 10 * 60 * 1000
	AccountPairingRequestMaxTTLMillis     int64 = 60 * 60 * 1000
	AccountClockSkewMillis                int64 = 5 * 60 * 1000
	AccountMaxDevices                           = 10
	AccountMaxRevokedDevices                    = 32
	AccountMaxCapabilities                      = 16
	AccountMaxCapabilityLength                  = 32
	AccountMaxPlatformLength                    = 64
	AccountMaxReasonLength                      = 160
	AccountNonceBytes                           = 16
	AccountRegistryKeyPrefix                    = "upeer-account-registry:"
	AccountRegistryTTLSeconds             int64 = 24 * 60 * 60
)

type DevicePairingRequestOptions struct {
	AccountUpeerID        string   `json:"accountUpeerId"`
	StaticX25519PublicKey string   `json:"staticX25519PublicKey,omitempty"`
	Platform              string   `json:"platform,omitempty"`
	Capabilities          []string `json:"capabilities,omitempty"`
	Nonce                 string   `json:"nonce,omitempty"`
	CreatedAt             int64    `json:"createdAt,omitempty"`
	TTLMillis             int64    `json:"ttlMillis,omitempty"`
}

type DevicePairingRequest struct {
	Version               int      `json:"version"`
	AccountUpeerID        string   `json:"accountUpeerId"`
	DeviceID              string   `json:"deviceId"`
	DevicePublicKey       string   `json:"devicePublicKey"`
	StaticX25519PublicKey string   `json:"staticX25519PublicKey,omitempty"`
	Platform              string   `json:"platform,omitempty"`
	Capabilities          []string `json:"capabilities,omitempty"`
	Nonce                 string   `json:"nonce"`
	CreatedAt             int64    `json:"createdAt"`
	ExpiresAt             int64    `json:"expiresAt"`
	Signature             string   `json:"signature"`
}

type AccountDeviceCertificate struct {
	Version               int      `json:"version"`
	AccountUpeerID        string   `json:"accountUpeerId"`
	AccountPublicKey      string   `json:"accountPublicKey"`
	DeviceID              string   `json:"deviceId"`
	DevicePublicKey       string   `json:"devicePublicKey"`
	StaticX25519PublicKey string   `json:"staticX25519PublicKey,omitempty"`
	Platform              string   `json:"platform,omitempty"`
	Capabilities          []string `json:"capabilities,omitempty"`
	IssuedAt              int64    `json:"issuedAt"`
	ExpiresAt             int64    `json:"expiresAt"`
	Epoch                 int64    `json:"epoch"`
	Signature             string   `json:"signature"`
}

type AccountRegistryOptions struct {
	Epoch     int64
	IssuedAt  int64
	TTLMillis int64
	NowMillis int64
	Devices   []AccountRegistryDevice
	Revoked   []AccountRevocation
}

type AccountRegistry struct {
	Version          int                     `json:"version"`
	AccountUpeerID   string                  `json:"accountUpeerId"`
	AccountPublicKey string                  `json:"accountPublicKey"`
	Epoch            int64                   `json:"epoch"`
	IssuedAt         int64                   `json:"issuedAt"`
	ExpiresAt        int64                   `json:"expiresAt"`
	Devices          []AccountRegistryDevice `json:"devices"`
	Revoked          []AccountRevocation     `json:"revoked"`
	Signature        string                  `json:"signature"`
}

type AccountRegistryDevice struct {
	DeviceID              string   `json:"deviceId"`
	DevicePublicKey       string   `json:"devicePublicKey"`
	StaticX25519PublicKey string   `json:"staticX25519PublicKey,omitempty"`
	Platform              string   `json:"platform,omitempty"`
	Capabilities          []string `json:"capabilities,omitempty"`
	AuthorizedAt          int64    `json:"authorizedAt"`
	ExpiresAt             int64    `json:"expiresAt"`
	Epoch                 int64    `json:"epoch"`
}

type AccountRevocation struct {
	DeviceID  string `json:"deviceId"`
	RevokedAt int64  `json:"revokedAt"`
	Reason    string `json:"reason"`
}
