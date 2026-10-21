import type { PeerPoolEntry } from '../../modules/upeer-transport/src/UPeerTransport.types';

export type PeerManagementSnapshot = {
  peers: PeerPoolEntry[];
  reservePeers: PeerPoolEntry[];
  selfLat: number | null;
  selfLon: number | null;
  statusAvailable: boolean;
  updatedAt: number;
};

export const EMPTY_PEER_MANAGEMENT: PeerManagementSnapshot = {
  peers: [],
  reservePeers: [],
  selfLat: null,
  selfLon: null,
  statusAvailable: false,
  updatedAt: 0,
};

export function createPeerManagementSnapshot(peers: PeerPoolEntry[], reservePeers: PeerPoolEntry[] = [], selfLat: number | null = null, selfLon: number | null = null): PeerManagementSnapshot {
  return {
    peers,
    reservePeers,
    selfLat,
    selfLon,
    statusAvailable: true,
    updatedAt: Date.now(),
  };
}