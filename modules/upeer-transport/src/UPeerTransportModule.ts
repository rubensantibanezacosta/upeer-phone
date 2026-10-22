import 'react-native-get-random-values';
import { ed25519 } from '@noble/curves/ed25519.js';
import { blake2b } from '@noble/hashes/blake2.js';
import { pbkdf2 } from '@noble/hashes/pbkdf2.js';
import { sha512 } from '@noble/hashes/sha2.js';
import { NativeModule, requireNativeModule } from 'expo';
import { validateMnemonic } from '../../../src/domain/mnemonic';
import type { AccountRegistryStatus, DeviceIdentityStatus, DhtLocationStatus, IdentityTransportStatus, InboundTransportStatus, LocationBlock, PeerInfo, PeerPoolEntry, ProtocolConversation, ProtocolMessage, ProtocolTransportStatus, YggdrasilNodeInfo } from './UPeerTransport.types';

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
  listenTCP(port: number): Promise<number>;
  acceptTCP(listenerID: number, timeoutMillis: number): Promise<number>;
  closeListener(listenerID: number): Promise<void>;
  readFrame(connectionId: number, timeoutMillis: number): Promise<number[]>;
  writeFrame(connectionId: number, payload: number[]): Promise<void>;
  validateControlFrame(payload: number[]): Promise<string>;
  inboundStatus(): Promise<string>;
  setIdentity(secretKey: number[]): Promise<void>;
  clearIdentity(): Promise<void>;
  identityStatus(): Promise<string>;
  sendChat(upeerId: string, content: string, replyTo: string): Promise<string>;
  editChat(upeerId: string, messageId: string, content: string): Promise<number>;
  messagesJSON(upeerId: string, limit: number): Promise<string>;
  conversationsJSON(): Promise<string>;
  markConversationRead(upeerId: string): Promise<void>;
  registerPeer(upeerId: string, address: string, publicKey: string, signedPreKeyJSON: string): Promise<boolean>;
  flushOutbox(): Promise<void>;
  protocolStatus(): Promise<string>;
  locationBlock(ttlMillis: number): Promise<string>;
  deviceIdentityStatus(): Promise<string>;
  accountRegistry(): Promise<string>;
  accountRegistryStatus(): Promise<string>;
  revokeAccountDevice(deviceId: string, reason: string): Promise<string>;
  validateDeviceAdmission(certificateJSON: string, registryJSON: string, expectedAccountUpeerId: string, nowMillis: number): Promise<boolean>;
  dhtLocationStatus(): Promise<string>;
  lookupContactLocation(upeerId: string): Promise<string>;
  verifyLocationBlock(blockJSON: string, publicKeyHex: string, nowMillis: number): Promise<boolean>;
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
  if (nodeSeed.length !== 32) throw new Error('La clave de conexión debe tener 32 bytes.');
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

export type PeerPoolSnapshot = {
  peers: PeerPoolEntry[];
  selfLat: number | null;
  selfLon: number | null;
};

export async function getPeerPool(): Promise<PeerPoolSnapshot> {
  const payload = JSON.parse(await nativeModule.peerPool()) as PeerPoolSnapshot | PeerPoolEntry[];
  if (Array.isArray(payload)) {
    return { peers: sortPeerPool(payload), selfLat: null, selfLon: null };
  }
  return {
    peers: sortPeerPool(Array.isArray(payload.peers) ? payload.peers : []),
    selfLat: validCoordinate(payload.selfLat, 90),
    selfLon: validCoordinate(payload.selfLon, 180),
  };
}

function sortPeerPool(entries: PeerPoolEntry[]): PeerPoolEntry[] {
  return entries.sort((left, right) => (right.score - left.score) || comparePeerUris(left.uri, right.uri));
}

function validCoordinate(value: number | null | undefined, limit: number): number | null {
  return typeof value === 'number' && Number.isFinite(value) && Math.abs(value) <= limit ? value : null;
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

export async function listenTCP(port: number): Promise<number> {
  return nativeModule.listenTCP(port);
}

export async function acceptTCP(listenerId: number, timeoutMillis: number): Promise<number> {
  return nativeModule.acceptTCP(listenerId, timeoutMillis);
}

export async function closeListener(listenerId: number): Promise<void> {
  return nativeModule.closeListener(listenerId);
}

export async function readFrame(connectionId: number, timeoutMillis: number): Promise<Uint8Array> {
  return Uint8Array.from(await nativeModule.readFrame(connectionId, timeoutMillis), (value) => value & 0xff);
}

export async function writeFrame(connectionId: number, payload: Uint8Array): Promise<void> {
  return nativeModule.writeFrame(connectionId, Array.from(payload, (value) => value & 0xff));
}

export async function validateControlFrame(payload: Uint8Array): Promise<string> {
  return nativeModule.validateControlFrame(Array.from(payload, (value) => value & 0xff));
}

export async function getInboundStatus(): Promise<InboundTransportStatus> {
  return JSON.parse(await nativeModule.inboundStatus()) as InboundTransportStatus;
}

export async function setIdentity(secretKey: Uint8Array): Promise<void> {
  if (secretKey.length !== 64) throw new Error('La clave privada uPeer debe tener 64 bytes.');
  return nativeModule.setIdentity(Array.from(secretKey, (value) => value & 0xff));
}

export async function clearIdentity(): Promise<void> {
  return nativeModule.clearIdentity();
}

export async function getIdentityStatus(): Promise<IdentityTransportStatus> {
  return JSON.parse(await nativeModule.identityStatus()) as IdentityTransportStatus;
}

export async function sendChat(upeerId: string, content: string, replyTo = ''): Promise<string> {
  return nativeModule.sendChat(upeerId, content, replyTo);
}

export async function editChat(upeerId: string, messageId: string, content: string): Promise<number> {
  return nativeModule.editChat(upeerId, messageId, content);
}

export async function getMessages(upeerId: string, limit = 100): Promise<ProtocolMessage[]> {
  return JSON.parse(await nativeModule.messagesJSON(upeerId, limit)) as ProtocolMessage[];
}

export async function getConversations(): Promise<ProtocolConversation[]> {
  return JSON.parse(await nativeModule.conversationsJSON()) as ProtocolConversation[];
}

export async function markConversationRead(upeerId: string): Promise<void> {
  return nativeModule.markConversationRead(upeerId);
}

export async function registerPeer(upeerId: string, address: string, publicKey: string, signedPreKeyJSON: string): Promise<boolean> {
  return nativeModule.registerPeer(upeerId, address, publicKey, signedPreKeyJSON);
}

export async function flushOutbox(): Promise<void> {
  return nativeModule.flushOutbox();
}

export async function getProtocolStatus(): Promise<ProtocolTransportStatus> {
  return JSON.parse(await nativeModule.protocolStatus()) as ProtocolTransportStatus;
}

export async function getLocationBlock(ttlMillis = 0): Promise<LocationBlock> {
  return JSON.parse(await nativeModule.locationBlock(ttlMillis)) as LocationBlock;
}

export async function getDeviceIdentityStatus(): Promise<DeviceIdentityStatus> {
  return JSON.parse(await nativeModule.deviceIdentityStatus()) as DeviceIdentityStatus;
}

export async function getAccountRegistry(): Promise<string> {
  return nativeModule.accountRegistry();
}

export async function getAccountRegistryStatus(): Promise<AccountRegistryStatus> {
  return JSON.parse(await nativeModule.accountRegistryStatus()) as AccountRegistryStatus;
}

export async function revokeAccountDevice(deviceId: string, reason: string): Promise<AccountRegistryStatus> {
  return JSON.parse(await nativeModule.revokeAccountDevice(deviceId, reason)) as AccountRegistryStatus;
}

export async function validateDeviceAdmission(
  certificateJSON: string,
  registryJSON: string,
  expectedAccountUpeerId: string,
  nowMillis = Date.now(),
): Promise<boolean> {
  return nativeModule.validateDeviceAdmission(certificateJSON, registryJSON, expectedAccountUpeerId, nowMillis);
}

export async function getDhtLocationStatus(): Promise<DhtLocationStatus> {
  return JSON.parse(await nativeModule.dhtLocationStatus()) as DhtLocationStatus;
}

export async function lookupContactLocation(upeerId: string): Promise<LocationBlock | null> {
  const blockJSON = await nativeModule.lookupContactLocation(upeerId);
  return blockJSON ? JSON.parse(blockJSON) as LocationBlock : null;
}

export async function verifyLocationBlock(blockJSON: string, publicKeyHex: string, nowMillis = Date.now()): Promise<boolean> {
  return nativeModule.verifyLocationBlock(blockJSON, publicKeyHex, nowMillis);
}

