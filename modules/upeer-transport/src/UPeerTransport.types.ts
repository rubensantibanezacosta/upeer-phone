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
};

export type ProtocolConversation = {
  upeerId: string;
  alias: string;
  lastMessage: string;
  lastStatus: ProtocolMessage['status'];
  lastTimestamp: number;
  lastIsMine: boolean;
  unread: number;
};

export type ProtocolTransportStatus = {
  identityReady: boolean;
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

export type PeerInfo = {
  URI: string;
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
