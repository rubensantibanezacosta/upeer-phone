import 'react-native-get-random-values';
import { ed25519 } from '@noble/curves/ed25519.js';
import { blake2b } from '@noble/hashes/blake2.js';
import { pbkdf2 } from '@noble/hashes/pbkdf2.js';
import { sha512 } from '@noble/hashes/sha2.js';
import { NativeModule, requireNativeModule } from 'expo';
import { validateMnemonic } from '../../../src/domain/mnemonic';
import { decodeEnrollmentTransfer, encodeEnrollmentTransfer } from '../../../src/domain/enrollment-transfer';
import type { AccountRegistryDocument, AccountRegistryStatus, ConversationActivityState, DeviceEnrollmentBeginResult, DeviceEnrollmentStatus, DeviceIdentityStatus, DhtLocationStatus, EnrollmentAuthorizerInvite, EnrollmentAuthorizerIssued, EnrollmentAuthorizerRequest, EnrollmentAuthorizerStatus, EnrollmentCertificateSummary, IdentityTransportStatus, InboundTransportStatus, LocationBlock, OutboundFileTransfer, PeerInfo, PeerPoolEntry, PeerSecurity, ProtocolConversation, ProtocolGroup, ProtocolMessage, ProtocolTransportStatus, SyncTransportStatus, YggdrasilNodeInfo } from './UPeerTransport.types';

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
  createGroup(name: string, memberIdsJSON: string, avatar: string): Promise<string>;
  inviteToGroup(groupId: string, upeerId: string): Promise<void>;
  removeGroupMember(groupId: string, upeerId: string): Promise<void>;
  transferGroupAdmin(groupId: string, upeerId: string): Promise<void>;
  leaveGroup(groupId: string): Promise<void>;
  renameGroup(groupId: string, name: string): Promise<void>;
  editChat(upeerId: string, messageId: string, content: string): Promise<number>;
  retryChat(upeerId: string, messageId: string): Promise<boolean>;
  messagesJSON(upeerId: string, limit: number): Promise<string>;
  searchMessagesJSON(query: string, limit: number, offset: number): Promise<string>;
  searchChatMessagesJSON(upeerId: string, query: string, limit: number, offset: number): Promise<string>;
  historyBeforeJSON(upeerId: string, beforeTimestamp: number, limit: number): Promise<string>;
  conversationsJSON(): Promise<string>;
  groupsJSON(): Promise<string>;
  peerSecurityJSON(upeerId: string): Promise<string>;
  markConversationRead(upeerId: string): Promise<void>;
  sendTyping(upeerId: string, state: ConversationActivityState): Promise<void>;
  markUserActive(): Promise<void>;
  sendReaction(upeerId: string, messageId: string, emoji: string, remove: boolean): Promise<void>;
  deleteMessage(upeerId: string, messageId: string): Promise<void>;
  deleteConversation(upeerId: string): Promise<boolean>;
  setBlockedPeers(blockedJSON: string): Promise<void>;
  setKnownContacts(knownJSON: string): Promise<void>;
  setProfile(alias: string, avatar: string): Promise<void>;
  setNotificationPrefs(prefsJSON: string): Promise<void>;
  setReadReceipts(enabled: boolean): Promise<void>;
  setMutedPeers(mutedJSON: string): Promise<void>;
  registerPeer(upeerId: string, address: string, publicKey: string, signedPreKeyJSON: string): Promise<boolean>;
  flushOutbox(): Promise<void>;
  protocolStatus(): Promise<string>;
  syncStatus(): Promise<string>;
  locationBlock(ttlMillis: number): Promise<string>;
  deviceIdentityStatus(): Promise<string>;
  accountRegistry(): Promise<string>;
  accountRegistryStatus(): Promise<string>;
  revokeAccountDevice(deviceId: string, reason: string): Promise<string>;
  validateDeviceAdmission(certificateJSON: string, registryJSON: string, expectedAccountUpeerId: string, nowMillis: number): Promise<boolean>;
  dhtLocationStatus(): Promise<string>;
  lookupContactLocation(upeerId: string): Promise<string>;
  verifyLocationBlock(blockJSON: string, publicKeyHex: string, nowMillis: number): Promise<boolean>;
  deviceEnrollmentBegin(code: string): Promise<string>;
  deviceEnrollmentRequestJSON(): Promise<string>;
  deviceEnrollmentSubmit(): Promise<string>;
  deviceEnrollmentFinish(sealedResponse: string): Promise<string>;
  deviceEnrollmentCancel(): Promise<void>;
  deviceEnrollmentStatusJSON(): Promise<string>;
  enrollmentAuthorizerCreateInvite(ttlMillis: number): Promise<string>;
  enrollmentAuthorizerAcceptRequest(requestCode: string): Promise<string>;
  enrollmentAuthorizerComplete(enrollmentJSON: string, ttlMillis: number): Promise<string>;
  enrollmentAuthorizerStatusJSON(): Promise<string>;
  enrollmentAuthorizerCancel(inviteId: string): Promise<void>;
  enrollmentAuthorizerCancelAll(): Promise<void>;
  deviceEnrollmentLastIssued(): Promise<string>;
  sendFile(
    upeerId: string,
    path: string,
    fileName: string,
    mimeType: string,
    caption: string,
    isVoiceNote: boolean,
    chatUpeerId: string,
    messageId: string,
  ): Promise<string>;
  cancelFile(fileId: string): Promise<boolean>;
  outboundFilesJSON(): Promise<string>;
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

export async function waitForIdentity(upeerId: string, timeoutMillis = 10_000): Promise<IdentityTransportStatus> {
  const expected = upeerId.trim().toLowerCase();
  const deadline = Date.now() + timeoutMillis;
  let last: IdentityTransportStatus = { ready: false };
  while (Date.now() < deadline) {
    last = await getIdentityStatus();
    if (last.ready && last.upeerId?.toLowerCase() === expected) return last;
    await new Promise((resolve) => { setTimeout(resolve, 150); });
  }
  throw new Error(`El servicio no confirmó la identidad de ${upeerId}.`);
}

export async function sendChat(upeerId: string, content: string, replyTo = ''): Promise<string> {
  return nativeModule.sendChat(upeerId, content, replyTo);
}

export async function createGroup(name: string, memberUpeerIds: string[], avatar = ''): Promise<string> {
  return nativeModule.createGroup(name, JSON.stringify(memberUpeerIds), avatar);
}

export async function inviteToGroup(groupId: string, upeerId: string): Promise<void> {
  return nativeModule.inviteToGroup(groupId, upeerId);
}

export async function removeGroupMember(groupId: string, upeerId: string): Promise<void> {
  return nativeModule.removeGroupMember(groupId, upeerId);
}

export async function transferGroupAdmin(groupId: string, upeerId: string): Promise<void> {
  return nativeModule.transferGroupAdmin(groupId, upeerId);
}

export async function leaveGroup(groupId: string): Promise<void> {
  return nativeModule.leaveGroup(groupId);
}

export async function renameGroup(groupId: string, name: string): Promise<void> {
  return nativeModule.renameGroup(groupId, name);
}

export async function editChat(upeerId: string, messageId: string, content: string): Promise<number> {
  return nativeModule.editChat(upeerId, messageId, content);
}

export async function retryChat(upeerId: string, messageId: string): Promise<boolean> {
  return nativeModule.retryChat(upeerId, messageId);
}

export async function getMessages(upeerId: string, limit = 100): Promise<ProtocolMessage[]> {
  return JSON.parse(await nativeModule.messagesJSON(upeerId, limit)) as ProtocolMessage[];
}

export type MessageSearchHit = {
  id: string;
  chatUpeerId: string;
  content: string;
  timestamp: number;
  isMine: boolean;
};

export async function searchMessages(query: string, limit = 100, offset = 0): Promise<MessageSearchHit[]> {
  return JSON.parse(await nativeModule.searchMessagesJSON(query, limit, offset)) as MessageSearchHit[];
}

export async function searchChatMessages(upeerId: string, query: string, limit = 50, offset = 0): Promise<MessageSearchHit[]> {
  return JSON.parse(await nativeModule.searchChatMessagesJSON(upeerId, query, limit, offset)) as MessageSearchHit[];
}

export async function getMessagesBefore(upeerId: string, beforeTimestamp: number, limit = 100): Promise<ProtocolMessage[]> {
  return JSON.parse(await nativeModule.historyBeforeJSON(upeerId, beforeTimestamp, limit)) as ProtocolMessage[];
}

export async function getConversations(): Promise<ProtocolConversation[]> {
  return JSON.parse(await nativeModule.conversationsJSON()) as ProtocolConversation[];
}

export async function getGroups(): Promise<ProtocolGroup[]> {
  return JSON.parse(await nativeModule.groupsJSON()) as ProtocolGroup[];
}

export async function getPeerSecurity(upeerId: string): Promise<PeerSecurity> {
  return JSON.parse(await nativeModule.peerSecurityJSON(upeerId)) as PeerSecurity;
}

export async function markConversationRead(upeerId: string): Promise<void> {
  return nativeModule.markConversationRead(upeerId);
}

export async function markUserActive(): Promise<void> {
  return nativeModule.markUserActive();
}

export async function sendTyping(upeerId: string, state: ConversationActivityState = 'typing'): Promise<void> {
  return nativeModule.sendTyping(upeerId, state);
}

export async function sendReaction(
  upeerId: string,
  messageId: string,
  emoji: string,
  remove: boolean,
): Promise<void> {
  return nativeModule.sendReaction(upeerId, messageId, emoji, remove);
}

export async function deleteMessage(upeerId: string, messageId: string): Promise<void> {
  return nativeModule.deleteMessage(upeerId, messageId);
}

export async function deleteConversation(upeerId: string): Promise<boolean> {
  return nativeModule.deleteConversation(upeerId);
}

export async function setBlockedPeers(blockedJSON: string): Promise<void> {
  return nativeModule.setBlockedPeers(blockedJSON);
}

export async function setKnownContacts(knownJSON: string): Promise<void> {
  return nativeModule.setKnownContacts(knownJSON);
}

export async function setProfile(alias: string, avatar: string): Promise<void> {
  return nativeModule.setProfile(alias, avatar);
}

export async function setNotificationPrefs(prefsJSON: string): Promise<void> {
  return nativeModule.setNotificationPrefs(prefsJSON);
}

export async function setReadReceipts(enabled: boolean): Promise<void> {
  return nativeModule.setReadReceipts(enabled);
}

export async function setMutedPeers(mutedJSON: string): Promise<void> {
  return nativeModule.setMutedPeers(mutedJSON);
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

export async function getSyncStatus(): Promise<SyncTransportStatus> {
  return JSON.parse(await nativeModule.syncStatus()) as SyncTransportStatus;
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

export async function getAccountRegistryDocument(): Promise<AccountRegistryDocument> {
  return JSON.parse(await nativeModule.accountRegistry()) as AccountRegistryDocument;
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

export async function beginDeviceEnrollment(code: string): Promise<DeviceEnrollmentBeginResult & { requestCode: string }> {
  const result = JSON.parse(await nativeModule.deviceEnrollmentBegin(code)) as DeviceEnrollmentBeginResult;
  return { ...result, requestCode: encodeEnrollmentTransfer(JSON.parse(result.request) as unknown) };
}

export type EnrollmentSubmitResult = { inviteId: string; authorizerAddress: string };

export async function submitDeviceEnrollment(): Promise<EnrollmentSubmitResult> {
  return JSON.parse(await nativeModule.deviceEnrollmentSubmit()) as EnrollmentSubmitResult;
}

export async function getDeviceEnrollmentRequest(): Promise<string> {
  const request = await nativeModule.deviceEnrollmentRequestJSON();
  return request ? encodeEnrollmentTransfer(JSON.parse(request) as unknown) : '';
}

export async function finishDeviceEnrollment(sealedCode: string): Promise<EnrollmentCertificateSummary> {
  const sealed = decodeEnrollmentTransfer(sealedCode);
  if (!sealed) throw new Error('El código de respuesta no es válido.');
  return JSON.parse(await nativeModule.deviceEnrollmentFinish(JSON.stringify(sealed))) as EnrollmentCertificateSummary;
}

export async function cancelDeviceEnrollment(): Promise<void> {
  return nativeModule.deviceEnrollmentCancel();
}

export async function getDeviceEnrollmentStatus(): Promise<DeviceEnrollmentStatus> {
  return JSON.parse(await nativeModule.deviceEnrollmentStatusJSON()) as DeviceEnrollmentStatus;
}

export async function createEnrollmentInvite(ttlMillis = 0): Promise<EnrollmentAuthorizerInvite> {
  return JSON.parse(await nativeModule.enrollmentAuthorizerCreateInvite(ttlMillis)) as EnrollmentAuthorizerInvite;
}

export async function acceptEnrollmentRequest(requestCode: string): Promise<EnrollmentAuthorizerRequest> {
  const sealed = decodeEnrollmentTransfer(requestCode);
  if (!sealed) throw new Error('El código de solicitud no es válido.');
  return JSON.parse(await nativeModule.enrollmentAuthorizerAcceptRequest(JSON.stringify(sealed))) as EnrollmentAuthorizerRequest;
}

export async function completeEnrollmentAuthorization(
  enrollment: EnrollmentAuthorizerRequest,
  ttlMillis = 0,
): Promise<EnrollmentAuthorizerIssued & { responseCode: string }> {
  const issued = JSON.parse(
    await nativeModule.enrollmentAuthorizerComplete(JSON.stringify(enrollment), ttlMillis),
  ) as EnrollmentAuthorizerIssued;
  return { ...issued, responseCode: encodeEnrollmentTransfer(JSON.parse(issued.code) as unknown) };
}

export async function getEnrollmentAuthorizerStatus(): Promise<EnrollmentAuthorizerStatus> {
  return JSON.parse(await nativeModule.enrollmentAuthorizerStatusJSON()) as EnrollmentAuthorizerStatus;
}

export async function cancelEnrollmentInvite(inviteId: string): Promise<void> {
  return nativeModule.enrollmentAuthorizerCancel(inviteId);
}

export async function cancelAllEnrollmentInvites(): Promise<void> {
  return nativeModule.enrollmentAuthorizerCancelAll();
}

export async function getLastIssuedEnrollment(): Promise<(EnrollmentAuthorizerIssued & { responseCode: string; inviteId: string }) | null> {
  const raw = await nativeModule.deviceEnrollmentLastIssued();
  if (!raw.trim()) return null;
  const issued = JSON.parse(raw) as EnrollmentAuthorizerIssued & { inviteId?: string };
  if (!issued.code) return null;
  return {
    ...issued,
    inviteId: issued.inviteId ?? '',
    responseCode: encodeEnrollmentTransfer(JSON.parse(issued.code) as unknown),
  };
}

export type SendFileInput = {
  upeerId: string;
  path: string;
  fileName: string;
  mimeType: string;
  caption?: string | null;
  isVoiceNote?: boolean;
  chatUpeerId?: string | null;
  messageId?: string | null;
};

export async function sendFile(input: SendFileInput): Promise<string> {
  return nativeModule.sendFile(
    input.upeerId,
    input.path,
    input.fileName,
    input.mimeType,
    input.caption ?? '',
    input.isVoiceNote === true,
    input.chatUpeerId ?? '',
    input.messageId ?? '',
  );
}

export async function cancelFile(fileId: string): Promise<boolean> {
  return nativeModule.cancelFile(fileId);
}

export async function getOutboundFiles(): Promise<OutboundFileTransfer[]> {
  return JSON.parse(await nativeModule.outboundFilesJSON()) as OutboundFileTransfer[];
}

