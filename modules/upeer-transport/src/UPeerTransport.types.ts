export type YggdrasilNodeInfo = {
  address: string;
  publicKey: string;
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
