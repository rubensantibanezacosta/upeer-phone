export type YggdrasilNodeInfo = {
  address: string;
  publicKey: string;
};

export type IdentityTransportStatus = {
  ready: boolean;
  publicKey?: string;
  upeerId?: string;
};

export type InboundTransportStatus = {
  listening: boolean;
  port: number;
  accepted: number;
  frames: number;
  valid: number;
  invalid: number;
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
  replyTo?: string;
  receivedAt?: number;
};

export type ProtocolConversation = {
  upeerId: string;
  alias: string;
  lastMessage: string;
  lastStatus: ProtocolMessage['status'];
  lastTimestamp: number;
  unread: number;
};

export type ProtocolTransportStatus = {
  identityReady: boolean;
  peers: number;
  sessions: number;
  messages: number;
  outbox: number;
  unread: number;
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
