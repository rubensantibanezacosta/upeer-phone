export type YggdrasilNodeInfo = {
  address: string;
  publicKey: string;
};

export type IdentityTransportStatus = {
  ready: boolean;
  publicKey?: string;
  upeerId?: string;
  deviceId?: string;
  deviceCertificateReady?: boolean;
};

export type InboundTransportStatus = {
  listening: boolean;
  port: number;
  accepted: number;
  frames: number;
  valid: number;
  invalid: number;
  limited: number;
  lastType: string;
  lastSenderUpeerId: string;
  lastSenderYggAddress: string;
  lastError: string;
  lastFrameAt: number;
};

export type ProtocolMessage = {
  id: string;
  upeerId: string;
  isMine: boolean;
  content: string;
  status: 'pending' | 'sent' | 'delivered' | 'read' | 'failed';
  timestamp: number;
  version?: number;
  isEdited?: boolean;
  editedAt?: number;
  replyTo?: string;
  receivedAt?: number;
  deleted?: boolean;
  deletedAt?: number;
  reactions?: Record<string, string[]>;
};

export type ProtocolConversation = {
  upeerId: string;
  alias: string;
  avatar?: string;
  lastMessage: string;
  lastStatus: ProtocolMessage['status'];
  lastTimestamp: number;
  lastIsMine: boolean;
  unread: number;
  typing?: boolean;
  lastSeen?: number;
  lastActive?: number;
};

export type PeerSecurity = {
  upeerId: string;
  publicKey: string;
  deviceId: string;
  devicePublicKey: string;
  hasSession: boolean;
  lastSeen: number;
  safetyNumber: string;
};

export type ProtocolTransportStatus = {  identityReady: boolean;
  peers: number;
  sessions: number;
  messages: number;
  outbox: number;
  unread: number;
  rateLimited: number;
  dht?: DhtRuntimeStatus;
};

export type DhtRuntimeStatus = {
  ready: boolean;
  nodeId?: string;
  contacts?: number;
  bootstrapped?: boolean;
  seedsLoaded?: number;
  lastSuccessAt?: number;
  nextAttemptAt?: number;
  published?: boolean;
  locationPublished?: boolean;
  remoteRegistries?: number;
};

export type DhtLocationStatus = {
  ready: boolean;
  upeerId?: string;
  dhtSeq: number;
  lastAddress?: string;
  deviceId?: string;
};

export type LocationBlock = {
  address: string;
  addresses?: string[];
  dhtSeq: number;
  expiresAt: number;
  signature: string;
  deviceId?: string;
  deviceMeta?: Record<string, unknown>;
  deviceCertificate?: Record<string, unknown>;
};

export type DeviceIdentityStatus = {
  ready: boolean;
  accountUpeerId?: string;
  deviceId?: string;
  certificateEpoch?: number;
  certificateExpiresAt?: number;
  certificate?: Record<string, unknown>;
};

export type AccountRegistryStatus = {
  ready: boolean;
  accountUpeerId?: string;
  epoch?: number;
  issuedAt?: number;
  expiresAt?: number;
  devices?: number;
  revoked?: number;
  localDeviceId?: string;
  localDeviceAdmitted?: boolean;
};

export type AccountRegistryDevice = {
  deviceId: string;
  devicePublicKey: string;
  staticX25519PublicKey?: string;
  platform?: string;
  capabilities?: string[];
  authorizedAt: number;
  expiresAt: number;
  epoch: number;
};

export type AccountRevocation = {
  deviceId: string;
  revokedAt: number;
  reason: string;
};

export type AccountRegistryDocument = {
  version: number;
  accountUpeerId: string;
  accountPublicKey: string;
  epoch: number;
  issuedAt: number;
  expiresAt: number;
  devices: AccountRegistryDevice[];
  revoked: AccountRevocation[];
  signature: string;
};

export type EnrollmentCertificateSummary = {
  accountUpeerId: string;
  accountPublicKey: string;
  deviceId: string;
  devicePublicKey: string;
  epoch: number;
  issuedAt: number;
  expiresAt: number;
  certificate: Record<string, unknown>;
};

export type DeviceEnrollmentStatus = {
  pending?: {
    accountUpeerId: string;
    authorizerDeviceId: string;
    expiresAt: number;
  };
  installed?: EnrollmentCertificateSummary;
};

export type DeviceEnrollmentBeginResult = {
  offer: {
    version: number;
    accountUpeerId: string;
    accountPublicKey: string;
    authorizerDeviceId: string;
    inviteId: string;
    expiresAt: number;
  };
  request: string;
};

export type EnrollmentAuthorizerInvite = {
  code: string;
  inviteId: string;
  expiresAt: number;
  accountUpeerId: string;
  authorizerDeviceId: string;
};

export type EnrollmentAuthorizerRequest = {
  inviteId: string;
  inviteJSON: string;
  requestJSON: string;
  deviceId: string;
  devicePublicKey: string;
  platform: string;
  capabilities: string[];
  expiresAt: number;
};

export type EnrollmentAuthorizerIssued = {
  code: string;
  accountUpeerId: string;
  deviceId: string;
  devicePublicKey: string;
  platform: string;
  epoch: number;
  issuedAt: number;
  expiresAt: number;
};

export type EnrollmentAuthorizerStatus = {
  invites: {
    inviteId: string;
    accountUpeerId: string;
    authorizerDeviceId: string;
    expiresAt: number;
  }[];
};

export type PeerInfo = {  URI: string;
  Up: boolean;
  Inbound: boolean;
  Port: number;
  Cost: number;
  Latency: number;
  Uptime: number;
  LastError?: string;
};

export type PeerPoolEntry = {
  uri: string;
  country: string;
  uptimePct: number;
  latencyMs: number | null;
  score: number;
  alive: boolean;
  distanceKm: number | null;
  host: string;
  lat: number | null;
  lon: number | null;
};
