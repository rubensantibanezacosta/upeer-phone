#!/usr/bin/env node
import net from 'node:net';
import crypto from 'node:crypto';
import { gunzipSync } from 'node:zlib';
import { ed25519 } from '@noble/curves/ed25519.js';
import sodium, { ready } from 'libsodium-wrappers';
import { canonicalStringify, frame, hex, required, socksConnect, toHex, writeAndClose } from './lib/network.mjs';
import { concat, createResponderState, decompress, hkdf, ratchetDecrypt } from './lib/ratchet.mjs';

await ready;

const target = required('UPEER_TARGET');
const port = Number(process.env.UPEER_PORT ?? 50005);
const senderYggAddress = required('UPEER_SENDER_ADDRESS');
const senderSeed = hex(process.env.UPEER_SEED_HEX ?? '0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20', 32);
const ratchetContext = process.env.UPEER_RATCHET_CONTEXT ?? 'direct';
const spkId = Number(process.env.UPEER_SPK_ID ?? Date.now());
const staleSpkId = process.env.UPEER_STALE_SPK_ID ? Number(process.env.UPEER_STALE_SPK_ID) : 0;
const staleHandshake = process.env.UPEER_STALE_HANDSHAKE === '1';
const listenPort = Number(process.env.UPEER_LISTEN_PORT ?? 0);
const expectChat = process.env.UPEER_EXPECT_MOBILE_CHAT === '1';
const acceptHandshake = process.env.UPEER_ACCEPT_HANDSHAKE !== '0';
const skipAckFirst = process.env.UPEER_SKIP_ACK_FIRST === '1';
const expectedMessages = Number(process.env.UPEER_EXPECT_MESSAGES ?? (expectChat ? 1 : 0));
const socksHost = process.env.UPEER_SOCKS_HOST ?? '127.0.0.1';
const socksPort = Number(process.env.UPEER_SOCKS_PORT ?? 9051);
const timeoutMillis = Number(process.env.UPEER_TIMEOUT_MS ?? 60000);

const senderKeys = sodium.crypto_sign_seed_keypair(senderSeed);
const senderPublicKey = toHex(senderKeys.publicKey);
const senderUpeerId = toHex(sodium.crypto_generichash(32, senderKeys.publicKey));
const senderCurveSecret = sodium.crypto_sign_ed25519_sk_to_curve25519(senderKeys.privateKey);
const freshSPK = deriveSignedPreKey(spkId);
const staleSPK = staleSpkId > 0 ? deriveSignedPreKey(staleSpkId) : null;
const sessions = new Map();
const seenMessages = new Set();
let received = 0;
let settled = false;
let server = null;

if (listenPort > 0) {
  server = startListener(listenPort);
  await waitForListener(server);
}
await sendReset(freshSPK);
if (staleSPK) await sendReset(staleSPK);
if (staleHandshake && staleSPK) await sendHandshake(staleSPK);
console.log(JSON.stringify({
  type: 'DR_RESET',
  target,
  spkId,
  spkPub: toHex(freshSPK.publicKey),
  staleSpkId: staleSpkId || null,
  staleHandshake: staleHandshake && !!staleSPK,
  ratchetContext,
  senderUpeerId,
  listening: listenPort || null,
}));
if (listenPort === 0 && !expectChat) succeed();

function deriveSignedPreKey(id) {
  const material = crypto.createHash('sha256')
    .update(Buffer.concat([Buffer.from(senderSeed), Buffer.from(`:${id}`)]))
    .digest();
  const keyPair = sodium.crypto_box_seed_keypair(material);
  const signature = toHex(sodium.crypto_sign_detached(keyPair.publicKey, senderKeys.privateKey));
  return { id, publicKey: keyPair.publicKey, privateKey: keyPair.privateKey, signature };
}

async function sendReset(signedPreKey) {
  const data = {
    type: 'DR_RESET',
    resetId: crypto.randomUUID(),
    signedPreKey: {
      spkId: signedPreKey.id,
      spkPub: toHex(signedPreKey.publicKey),
      spkSig: signedPreKey.signature,
    },
    ratchetContext,
    isInternalSync: false,
    senderUpeerId,
    senderYggAddress,
  };
  data.signature = signPacket(data);
  await sendFrame(target, data);
}

async function sendHandshake(signedPreKey) {
  const data = {
    type: 'HANDSHAKE_REQ',
    publicKey: senderPublicKey,
    signedPreKey: {
      spkId: signedPreKey.id,
      spkPub: toHex(signedPreKey.publicKey),
      spkSig: signedPreKey.signature,
    },
    alias: 'Desktop stale handshake',
    isInternalSync: false,
    senderUpeerId,
    senderYggAddress,
  };
  data.signature = signPacket(data);
  await sendFrame(target, data);
}

function startListener(listenPortValue) {
  const listener = net.createServer((connection) => {
    let buffer = Buffer.alloc(0);
    connection.on('data', (chunk) => {
      buffer = Buffer.concat([buffer, chunk]);
      while (buffer.length >= 4) {
        const frameLength = buffer.readUInt32BE(0);
        if (frameLength <= 0 || frameLength > 10 * 1024 * 1024) return fail(new Error(`invalid frame length ${frameLength}`));
        if (buffer.length < frameLength + 4) return;
        const payload = buffer.subarray(4, frameLength + 4);
        buffer = buffer.subarray(frameLength + 4);
        void handlePayload(payload).catch(fail);
      }
    });
    connection.on('error', fail);
  });
  listener.on('error', fail);
  listener.listen(listenPortValue, '127.0.0.1');
  setTimeout(() => fail(new Error('timeout waiting for mobile chat')), timeoutMillis).unref();
  return listener;
}

function waitForListener(listener) {
  return new Promise((resolve) => listener.once('listening', resolve));
}

async function handlePayload(payload) {
  const packet = JSON.parse(payload.toString('utf8'));
  if (packet.type === 'HANDSHAKE_REQ') {
    verifySigned(packet, packet.publicKey);
    console.log(JSON.stringify({ type: 'HANDSHAKE_REQ', senderYggAddress: packet.senderYggAddress, senderUpeerId: packet.senderUpeerId }));
    if (acceptHandshake) await sendAccept(packet.senderYggAddress);
    return;
  }
  if (packet.type === 'ACK') {
    console.log(JSON.stringify({ type: 'ACK', id: packet.id, status: packet.status }));
    if (expectedMessages === 0) succeed();
    return;
  }
  if (packet.type === 'HANDSHAKE_ACCEPT') {
    console.log(JSON.stringify({ type: 'HANDSHAKE_ACCEPT', senderUpeerId: packet.senderUpeerId }));
    return;
  }
  if (packet.type !== 'SEALED') throw new Error(`unexpected packet type ${packet.type}`);
  const inner = openSealed(packet.ciphertext);
  if (!inner) throw new Error('could not open SEALED packet');
  if (inner.type !== 'CHAT') throw new Error(`unexpected inner type ${inner.type}`);
  const session = sessionFor(inner);
  verifySigned(inner, session.publicKey);
  if (seenMessages.has(inner.id)) {
    received += 1;
    console.log(JSON.stringify({ type: 'CHAT_DUPLICATE', id: inner.id, x3dhSpkId: inner.x3dhInit?.spkId ?? null }));
    await sendAck(inner.senderYggAddress, inner.id);
    if (received >= expectedMessages) succeed();
    return;
  }
  const plaintext = ratchetDecrypt(session.state, inner.ratchetHeader, inner.content, inner.nonce);
  if (!plaintext) throw new Error('ratchet decrypt with the fresh signed pre-key failed');
  const text = decompress(Buffer.from(plaintext).toString('utf8'));
  seenMessages.add(inner.id);
  received += 1;
  console.log(JSON.stringify({ type: 'CHAT', id: inner.id, text, x3dhSpkId: inner.x3dhInit?.spkId ?? null, sealedBytes: packet.ciphertext.length / 2 }));
  if (!(skipAckFirst && received === 1)) await sendAck(inner.senderYggAddress, inner.id);
  if (received >= expectedMessages) succeed();
}

function sessionFor(chat) {
  const existing = sessions.get(chat.senderUpeerId);
  if (existing) return existing;
  const init = chat.x3dhInit;
  if (!init) throw new Error('first chat after DR_RESET is missing x3dhInit');
  if (init.spkId !== spkId) throw new Error(`mobile used stale spkId ${init.spkId}, expected ${spkId}`);
  const mobileIdentityPublicKey = Buffer.from(init.ikPub, 'hex');
  const ephemeralPublicKey = Buffer.from(init.ekPub, 'hex');
  const mobileCurvePublic = sodium.crypto_sign_ed25519_pk_to_curve25519(mobileIdentityPublicKey);
  const dh1 = sodium.crypto_scalarmult(freshSPK.privateKey, mobileCurvePublic);
  const dh2 = sodium.crypto_scalarmult(senderCurveSecret, ephemeralPublicKey);
  const dh3 = sodium.crypto_scalarmult(freshSPK.privateKey, ephemeralPublicKey);
  const [sharedSecret] = hkdf(new Uint8Array(32), concat(dh1, dh2, dh3), 'X3DHv1.0');
  const created = {
    publicKey: init.ikPub,
    state: createResponderState(sharedSecret, freshSPK),
  };
  sessions.set(chat.senderUpeerId, created);
  return created;
}


async function sendAccept(mobileAddress) {
  const data = {
    type: 'HANDSHAKE_ACCEPT',
    publicKey: senderPublicKey,
    signedPreKey: { spkId, spkPub: toHex(freshSPK.publicKey), spkSig: freshSPK.signature },
    alias: 'Desktop DR_RESET',
    isInternalSync: false,
    senderUpeerId,
    senderYggAddress,
  };
  data.signature = signPacket(data);
  await sendFrame(mobileAddress, data);
}

async function sendAck(mobileAddress, messageID) {
  const data = {
    type: 'ACK',
    id: messageID,
    status: 'delivered',
    isInternalSync: false,
    senderUpeerId,
    senderYggAddress,
  };
  data.signature = signPacket(data);
  await sendFrame(mobileAddress, data);
}

function openSealed(ciphertextHex) {
  try {
    const plaintext = sodium.crypto_box_seal_open(
      Buffer.from(ciphertextHex, 'hex'),
      sodium.crypto_sign_ed25519_pk_to_curve25519(senderKeys.publicKey),
      senderCurveSecret,
    );
    return JSON.parse(Buffer.from(plaintext).toString('utf8'));
  } catch {
    return null;
  }
}

function verifySigned(packet, publicKeyHex) {
  if (typeof publicKeyHex !== 'string' || publicKeyHex.length !== 64) throw new Error('invalid publicKey');
  if (!packet.senderYggAddress) throw new Error('missing senderYggAddress');
  if (typeof packet.signature !== 'string' || packet.signature.length !== 128) throw new Error('invalid signature');
  const publicKey = Buffer.from(publicKeyHex, 'hex');
  if (packet.senderUpeerId !== toHex(sodium.crypto_generichash(32, publicKey))) throw new Error('senderUpeerId mismatch');
  const payload = { ...packet };
  delete payload.signature;
  delete payload.senderUpeerId;
  delete payload.senderYggAddress;
  const signed = { ...payload, senderUpeerId: packet.senderUpeerId, senderYggAddress: packet.senderYggAddress };
  if (!ed25519.verify(Buffer.from(packet.signature, 'hex'), new TextEncoder().encode(canonicalStringify(signed)), publicKey)) {
    throw new Error('invalid signed packet signature');
  }
}

function signPacket(packet) {
  const payload = { ...packet };
  delete payload.signature;
  return toHex(sodium.crypto_sign_detached(new TextEncoder().encode(canonicalStringify(payload)), senderKeys.privateKey));
}

async function sendFrame(address, packet) {
  const connection = await socksConnect(socksHost, socksPort, address, port, 10000);
  try {
    await writeAndClose(connection, frame(Buffer.from(JSON.stringify(packet), 'utf8')));
  } finally {
    connection.destroy();
  }
}

function succeed() {
  if (settled) return;
  settled = true;
  console.log(JSON.stringify({ verified: true, received }));
  server?.close();
  setTimeout(() => process.exit(0), 20).unref();
}

function fail(cause) {
  if (settled) return;
  settled = true;
  console.error(JSON.stringify({ verified: false, error: cause instanceof Error ? cause.message : String(cause) }));
  server?.close();
  setTimeout(() => process.exit(1), 20).unref();
}

