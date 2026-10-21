import 'react-native-get-random-values';
import { ed25519 } from '@noble/curves/ed25519.js';
import { blake2b } from '@noble/hashes/blake2.js';
import { pbkdf2 } from '@noble/hashes/pbkdf2.js';
import { sha512 } from '@noble/hashes/sha2.js';
import { NativeModule, requireNativeModule } from 'expo';
import { validateMnemonic } from '../../../src/domain/mnemonic';
import type { PeerInfo, PeerPoolEntry, YggdrasilNodeInfo } from './UPeerTransport.types';

declare class UPeerTransportModule extends NativeModule<{}> {
  start(configJSON: number[]): Promise<YggdrasilNodeInfo>;
  nodeInfo(): Promise<YggdrasilNodeInfo | null>;
  startWithPeers(peers: string[], nodeSeed: number[]): Promise<YggdrasilNodeInfo>;
  stop(): Promise<void>;
  setForeground(): Promise<void>;
  peerStatus(): Promise<string>;
  peerPool(): Promise<string>;
  connectedPeerCount(): Promise<number>;
  retryPeers(): Promise<void>;
  dialTCP(address: string, port: number): Promise<number>;
  dialUDP(address: string, port: number): Promise<number>;
  writeTCP(connectionId: number, payload: number[]): Promise<void>;
  writeUDP(connectionId: number, payload: number[]): Promise<void>;
  read(connectionId: number, maxBytes: number, timeoutMillis: number): Promise<number[]>;
  closeConnection(connectionId: number): Promise<void>;
  generateNodeSeed(): Promise<number[]>;
}

const nativeModule = requireNativeModule<UPeerTransportModule>('UPeerTransport');

export async function deriveMnemonicIdentity(mnemonicInput: string): Promise<{ publicKey: string; secretKey: string; upeerId: string }> {
  const mnemonic = mnemonicInput.normalize('NFKD').trim().toLowerCase().split(/\s+/).join(' ');
  if (!validateMnemonic(mnemonic)) throw new Error('La frase de recuperación no es válida.');
  const password = new TextEncoder().encode(mnemonic);
  const salt = new TextEncoder().encode('mnemonic');
  const fullSeed = pbkdf2(sha512, password, salt, { c: 2048, dkLen: 64 });
  password.fill(0);
  salt.fill(0);
  const seed = fullSeed.slice(0, 32);
  try {
    const publicKey = ed25519.getPublicKey(seed);
    const secretKey = concatBytes(seed, publicKey);
    const upeerId = blake2b(publicKey, { dkLen: 32 });
    return { publicKey: toHex(publicKey), secretKey: toHex(secretKey), upeerId: toHex(upeerId) };
  } finally {
    seed.fill(0);
    fullSeed.fill(0);
  }
}

function concatBytes(first: Uint8Array, second: Uint8Array): Uint8Array {
  const result = new Uint8Array(first.length + second.length);
  result.set(first);
  result.set(second, first.length);
  return result;
}

function toHex(value: Uint8Array): string {
  return Array.from(value, (byte) => byte.toString(16).padStart(2, '0')).join('');
}

export async function start(configJSON: Uint8Array): Promise<YggdrasilNodeInfo> {
  return nativeModule.start(Array.from(configJSON, (value) => value & 0xff));
}

export async function getNodeInfo(): Promise<YggdrasilNodeInfo | null> {
  return nativeModule.nodeInfo();
}

export async function startWithPeers(peers: string[], nodeSeed: Uint8Array): Promise<YggdrasilNodeInfo> {
  if (nodeSeed.length !== 32) throw new Error('La semilla del nodo Yggdrasil debe tener 32 bytes.');
  return nativeModule.startWithPeers(peers, Array.from(nodeSeed, (value) => value & 0xff));
}

export async function stop(): Promise<void> {
  return nativeModule.stop();
}

export async function generateNodeSeed(): Promise<Uint8Array> {
  return Uint8Array.from(await nativeModule.generateNodeSeed());
}

export async function setTransportForeground(): Promise<void> {
  return nativeModule.setForeground();
}

function comparePeerUris(left: string, right: string): number {
  return left < right ? -1 : left > right ? 1 : 0;
}

export async function getPeerStatus(): Promise<PeerInfo[]> {
  const peers = JSON.parse(await nativeModule.peerStatus()) as PeerInfo[];
  return peers.sort((left, right) => comparePeerUris(left.URI, right.URI));
}

export async function getPeerPool(): Promise<PeerPoolEntry[]> {
  const entries = JSON.parse(await nativeModule.peerPool()) as PeerPoolEntry[];
  return entries.sort((left, right) => (right.score - left.score) || comparePeerUris(left.uri, right.uri));
}

export function getConnectedPeerCount(): Promise<number> {
  return nativeModule.connectedPeerCount();
}

export async function retryPeers(): Promise<void> {
  return nativeModule.retryPeers();
}

export async function dialTCP(address: string, port: number): Promise<number> {
  return nativeModule.dialTCP(address, port);
}

export async function dialUDP(address: string, port: number): Promise<number> {
  return nativeModule.dialUDP(address, port);
}

export async function writeTCP(connectionId: number, payload: Uint8Array): Promise<void> {
  return nativeModule.writeTCP(connectionId, Array.from(payload, (value) => value & 0xff));
}

export async function closeConnection(connectionId: number): Promise<void> {
  return nativeModule.closeConnection(connectionId);
}

export async function writeUDP(connectionId: number, payload: Uint8Array): Promise<void> {
  return nativeModule.writeUDP(connectionId, Array.from(payload, (value) => value & 0xff));
}

export async function readConnection(connectionId: number, maxBytes: number, timeoutMillis: number): Promise<Uint8Array> {
  return Uint8Array.from(await nativeModule.read(connectionId, maxBytes, timeoutMillis), (value) => value & 0xff);
}

