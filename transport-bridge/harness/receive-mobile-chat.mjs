#!/usr/bin/env node
import net from 'node:net';
import crypto from 'node:crypto';
import { gunzipSync } from 'node:zlib';
import { ed25519 } from '@noble/curves/ed25519.js';
import sodium, { ready } from 'libsodium-wrappers';

await ready;

const port = Number(process.env.UPEER_LISTEN_PORT ?? 50105);
const desktopAddress = required('UPEER_SENDER_ADDRESS');
const desktopSeed = hex(process.env.UPEER_SEED_HEX ?? '1111111111111111111111111111111111111111111111111111111111111111', 32);
const socksHost = process.env.UPEER_SOCKS_HOST ?? '127.0.0.1';
const socksPort = Number(process.env.UPEER_SOCKS_PORT ?? 9051);
const expectedMessages = Number(process.env.UPEER_EXPECT_MESSAGES ?? 1);
const skipAckFirst = process.env.UPEER_SKIP_ACK_FIRST === '1';
const timeoutMillis = Number(process.env.UPEER_TIMEOUT_MS ?? 120000);
const desktopKeys = sodium.crypto_sign_seed_keypair(desktopSeed);
const desktopPublicKey = toHex(desktopKeys.publicKey);
const desktopUpeerId = toHex(sodium.crypto_generichash(32, desktopKeys.publicKey));
const desktopCurvePublic = sodium.crypto_sign_ed25519_pk_to_curve25519(desktopKeys.publicKey);
const desktopCurveSecret = sodium.crypto_sign_ed25519_sk_to_curve25519(desktopKeys.privateKey);
const signedPreKeyId = Number(process.env.UPEER_SPK_ID ?? Date.now());
const signedPreKey = process.env.UPEER_SPK_ID ? deriveSignedPreKey(signedPreKeyId) : sodium.crypto_box_keypair();
const signedPreKeySignature = toHex(sodium.crypto_sign_detached(signedPreKey.publicKey, desktopKeys.privateKey));
const sessions = new Map();
const seenMessages = new Set();
let received = 0;
let settled = false;

const server = net.createServer((connection) => {
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

server.on('error', fail);
server.listen(port, '127.0.0.1', () => {
  console.log(JSON.stringify({ listening: true, port, desktopPublicKey, desktopUpeerId, signedPreKeyId }));
});
setTimeout(() => fail(new Error('timeout waiting for mobile chat')), timeoutMillis).unref();

async function handlePayload(payload) {
  const packet = JSON.parse(payload.toString('utf8'));
  if (packet.type === 'SEALED') {
    const inner = openSealed(packet.ciphertext);
    if (!inner) throw new Error('could not open SEALED packet');
    return handleChat(inner, packet.ciphertext);
  }
  if (packet.type === 'HANDSHAKE_REQ') {
    verifySigned(packet, packet.publicKey);
    console.log(JSON.stringify({ type: 'HANDSHAKE_REQ', publicKey: packet.publicKey, senderUpeerId: packet.senderUpeerId, senderYggAddress: packet.senderYggAddress }));
    await sendAccept(packet.senderYggAddress);
    return;
  }
  throw new Error(`unexpected packet type ${packet.type}`);
}

async function handleChat(chat, sealedCiphertext) {
  const session = sessions.get(chat.senderUpeerId);
  if (!session) {
    if (!chat.x3dhInit) throw new Error('first CHAT is missing x3dhInit');
    const created = createResponderSession(chat.x3dhInit);
    sessions.set(chat.senderUpeerId, created);
  }
  const activeSession = sessions.get(chat.senderUpeerId);
  verifySigned(chat, activeSession.publicKey);
  if (chat.type !== 'CHAT') throw new Error(`unexpected inner type ${chat.type}`);
  if (seenMessages.has(chat.id)) {
    received += 1;
    console.log(JSON.stringify({ type: 'CHAT_DUPLICATE', id: chat.id, senderUpeerId: chat.senderUpeerId }));
    await sendAck(chat.senderYggAddress, chat.id);
    if (received >= expectedMessages) succeed();
    return;
  }
  const plaintext = ratchetDecrypt(activeSession.state, chat.ratchetHeader, chat.content, chat.nonce);
  if (!plaintext) throw new Error('ratchet decrypt failed');
  const text = decompress(Buffer.from(plaintext).toString('utf8'));
  seenMessages.add(chat.id);
  received += 1;
  console.log(JSON.stringify({ type: 'CHAT', id: chat.id, senderUpeerId: chat.senderUpeerId, text, sealedBytes: sealedCiphertext.length / 2 }));
  if (!(skipAckFirst && received === 1)) await sendAck(chat.senderYggAddress, chat.id);
  if (received >= expectedMessages) succeed();
}

function createResponderSession(init) {
  const mobileIdentityPublicKey = Buffer.from(init.ikPub, 'hex');
  const ephemeralPublicKey = Buffer.from(init.ekPub, 'hex');
  if (init.spkId !== signedPreKeyId) throw new Error(`unexpected spkId ${init.spkId}`);
  const mobileCurvePublic = sodium.crypto_sign_ed25519_pk_to_curve25519(mobileIdentityPublicKey);
  const dh1 = sodium.crypto_scalarmult(signedPreKey.privateKey, mobileCurvePublic);
  const dh2 = sodium.crypto_scalarmult(desktopCurveSecret, ephemeralPublicKey);
  const dh3 = sodium.crypto_scalarmult(signedPreKey.privateKey, ephemeralPublicKey);
  const [sharedSecret] = hkdf(new Uint8Array(32), concat(dh1, dh2, dh3), 'X3DHv1.0');
  return {
    publicKey: init.ikPub,
    state: {
      rk: sharedSecret,
      cks: null,
      ckr: null,
      ns: 0,
      nr: 0,
      pn: 0,
      dhsPk: signedPreKey.publicKey,
      dhsSk: signedPreKey.privateKey,
      dhr: null,
      skipped: new Map(),
    },
  };
}

function ratchetDecrypt(state, header, ciphertextHex, nonceHex) {
  const remotePublicKey = Buffer.from(header.dh, 'hex');
  const nonce = Buffer.from(nonceHex, 'hex');
  const ciphertext = Buffer.from(ciphertextHex, 'hex');
  if (!state.dhr || !remotePublicKey.equals(state.dhr)) {
    if (state.ckr) skipKeys(state, header.pn);
    dhRatchetStep(state, remotePublicKey);
  }
  skipKeys(state, header.n);
  if (!state.ckr) return null;
  const [messageKey, nextChainKey] = chainStep(state.ckr);
  try {
    const plaintext = sodium.crypto_secretbox_open_easy(ciphertext, nonce, messageKey);
    state.ckr = nextChainKey;
    state.nr += 1;
    return plaintext;
  } catch {
    return null;
  }
}

function dhRatchetStep(state, remotePublicKey) {
  state.pn = state.ns;
  state.ns = 0;
  state.nr = 0;
  const dh1 = sodium.crypto_scalarmult(state.dhsSk, remotePublicKey);
  [state.rk, state.ckr] = hkdf(state.rk, dh1, 'ratchet1');
  const nextKeyPair = sodium.crypto_box_keypair();
  state.dhsPk = nextKeyPair.publicKey;
  state.dhsSk = nextKeyPair.privateKey;
  state.dhr = Buffer.from(remotePublicKey);
  const dh2 = sodium.crypto_scalarmult(state.dhsSk, remotePublicKey);
  [state.rk, state.cks] = hkdf(state.rk, dh2, 'ratchet1');
}

function skipKeys(state, until) {
  if (!state.ckr) return;
  while (state.nr < until) {
    const [messageKey, nextChainKey] = chainStep(state.ckr);
    state.skipped.set(`${toHex(state.dhr)}:${state.nr}`, messageKey);
    state.ckr = nextChainKey;
    state.nr += 1;
  }
}

async function sendAccept(mobileAddress) {
  const data = {
    type: 'HANDSHAKE_ACCEPT',
    publicKey: desktopPublicKey,
    signedPreKey: { spkId: signedPreKeyId, spkPub: toHex(signedPreKey.publicKey), spkSig: signedPreKeySignature },
    alias: 'Desktop receiver',
    isInternalSync: false,
    senderUpeerId: desktopUpeerId,
    senderYggAddress: desktopAddress,
  };
  data.signature = signPacket(data);
  await sendFrame(mobileAddress, data);
  console.log(JSON.stringify({ type: 'HANDSHAKE_ACCEPT', to: mobileAddress }));
}

async function sendAck(mobileAddress, messageID) {
  const data = {
    type: 'ACK',
    id: messageID,
    status: 'delivered',
    isInternalSync: false,
    senderUpeerId: desktopUpeerId,
    senderYggAddress: desktopAddress,
  };
  data.signature = signPacket(data);
  await sendFrame(mobileAddress, data);
  console.log(JSON.stringify({ type: 'ACK', id: messageID, to: mobileAddress }));
}

function signPacket(packet) {
  const payload = { ...packet };
  delete payload.signature;
  return toHex(sodium.crypto_sign_detached(new TextEncoder().encode(canonicalStringify(payload)), desktopKeys.privateKey));
}

function deriveSignedPreKey(id) {
  const material = crypto.createHash('sha256')
    .update(Buffer.concat([Buffer.from(desktopSeed), Buffer.from(`:${id}`)]))
    .digest();
  const keyPair = sodium.crypto_box_seed_keypair(material);
  return { id, publicKey: keyPair.publicKey, privateKey: keyPair.privateKey };
}

async function sendFrame(address, packet) {
  const connection = await socksConnect(socksHost, socksPort, address, 50005, 10000);
  try {
    await writeAndClose(connection, frame(Buffer.from(JSON.stringify(packet), 'utf8')));
  } finally {
    connection.destroy();
  }
}

function openSealed(ciphertextHex) {
  try {
    const plaintext = sodium.crypto_box_seal_open(Buffer.from(ciphertextHex, 'hex'), desktopCurvePublic, desktopCurveSecret);
    return JSON.parse(Buffer.from(plaintext).toString('utf8'));
  } catch {
    return null;
  }
}

function verifySigned(packet, publicKeyHex) {
  if (!packet.senderYggAddress) throw new Error('missing senderYggAddress');
  if (typeof packet.signature !== 'string' || packet.signature.length !== 128) throw new Error('invalid signature');
  const publicKey = Buffer.from(publicKeyHex, 'hex');
  const derivedId = toHex(sodium.crypto_generichash(32, publicKey));
  if (packet.senderUpeerId !== derivedId) throw new Error('senderUpeerId mismatch');
  const payload = { ...packet };
  delete payload.signature;
  delete payload.senderUpeerId;
  delete payload.senderYggAddress;
  const signed = { ...payload, senderUpeerId: packet.senderUpeerId, senderYggAddress: packet.senderYggAddress };
  if (!ed25519.verify(Buffer.from(packet.signature, 'hex'), new TextEncoder().encode(canonicalStringify(signed)), publicKey)) {
    throw new Error('invalid signed packet signature');
  }
}

function hkdf(rootKey, inputMaterial, info) {
  const prk = sodium.crypto_generichash(32, inputMaterial, rootKey);
  return [
    sodium.crypto_kdf_derive_from_key(32, 1, info, prk),
    sodium.crypto_kdf_derive_from_key(32, 2, info, prk),
  ];
}

function chainStep(chainKey) {
  const messageKey = new Uint8Array(crypto.createHmac('sha256', chainKey).update(Buffer.from([1])).digest());
  const nextChainKey = new Uint8Array(crypto.createHmac('sha256', chainKey).update(Buffer.from([2])).digest());
  return [messageKey, nextChainKey];
}

function decompress(text) {
  if (!text.startsWith('\u2763UP\u2763')) return text;
  try {
    return gunzipSync(Buffer.from(text.slice(4), 'base64')).toString('utf8');
  } catch {
    return text;
  }
}

function canonicalStringify(value) {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) {
    if (Array.isArray(value)) return `[${value.map(canonicalStringify).join(',')}]`;
    return JSON.stringify(value);
  }
  const entries = Object.keys(value)
    .sort()
    .map((key) => (value[key] === undefined ? null : `${JSON.stringify(key)}:${canonicalStringify(value[key])}`))
    .filter((entry) => entry !== null);
  return `{${entries.join(',')}}`;
}

function concat(...values) {
  const result = new Uint8Array(values.reduce((sum, value) => sum + value.length, 0));
  let offset = 0;
  for (const value of values) {
    result.set(value, offset);
    offset += value.length;
  }
  return result;
}

function frame(payload) {
  const result = Buffer.alloc(payload.length + 4);
  result.writeUInt32BE(payload.length, 0);
  payload.copy(result, 4);
  return result;
}

function parseIPv6(value) {
  const parts = value.replace(/^\[|\]$/g, '').split('::');
  const left = parts[0] ? parts[0].split(':') : [];
  const right = parts.length === 2 && parts[1] ? parts[1].split(':') : [];
  const groups = parts.length === 2
    ? [...left, ...Array(8 - left.length - right.length).fill('0'), ...right]
    : left;
  if (groups.length !== 8) throw new Error(`Invalid IPv6 address: ${value}`);
  const buffer = Buffer.alloc(16);
  groups.forEach((group, index) => buffer.writeUInt16BE(Number.parseInt(group, 16), index * 2));
  return buffer;
}

function socksConnect(host, socksPortValue, targetHost, targetPort, timeout) {
  return new Promise((resolve, reject) => {
    const connection = net.createConnection({ host, port: socksPortValue });
    let buffer = Buffer.alloc(0);
    let stage = 'greeting';
    const fail = (cause) => {
      connection.destroy();
      reject(cause);
    };
    connection.setTimeout(timeout, () => fail(new Error('SOCKS5 timeout')));
    connection.once('error', fail);
    connection.once('connect', () => connection.write(Buffer.from([5, 1, 0])));
    connection.on('data', (chunk) => {
      buffer = Buffer.concat([buffer, chunk]);
      if (stage === 'greeting') {
        if (buffer.length < 2) return;
        if (buffer[0] !== 5 || buffer[1] !== 0) return fail(new Error(`SOCKS5 auth rejected: ${buffer[1]}`));
        buffer = buffer.subarray(2);
        stage = 'connect';
        const request = Buffer.alloc(22);
        request[0] = 5;
        request[1] = 1;
        request[2] = 0;
        request[3] = 4;
        parseIPv6(targetHost).copy(request, 4);
        request.writeUInt16BE(targetPort, 20);
        connection.write(request);
        return;
      }
      if (buffer.length < 4) return;
      const addressLength = buffer[3] === 4 ? 16 : buffer[3] === 1 ? 4 : buffer[4] + 1;
      if (buffer.length < 4 + addressLength + 2) return;
      if (buffer[1] !== 0) return fail(new Error(`SOCKS5 connect failed: ${buffer[1]}`));
      connection.removeAllListeners('data');
      connection.setTimeout(0);
      resolve(connection);
    });
  });
}

function writeAndClose(connection, payload) {
  return new Promise((resolve, reject) => {
    connection.once('error', reject);
    connection.write(payload, (cause) => {
      if (cause) return reject(cause);
      connection.end();
      resolve();
    });
  });
}

function succeed() {
  if (settled) return;
  settled = true;
  console.log(JSON.stringify({ verified: true, received }));
  server.close();
  setTimeout(() => process.exit(0), 20).unref();
}

function fail(cause) {
  if (settled) return;
  settled = true;
  console.error(JSON.stringify({ verified: false, error: cause instanceof Error ? cause.message : String(cause) }));
  server.close();
  setTimeout(() => process.exit(1), 20).unref();
}

function required(name) {
  const value = process.env[name];
  if (!value) throw new Error(`${name} is required`);
  return value;
}

function hex(value, expectedBytes) {
  if (!new RegExp(`^[0-9a-f]{${expectedBytes * 2}}$`, 'i').test(value)) throw new Error(`Expected ${expectedBytes} bytes of hexadecimal`);
  return new Uint8Array(Buffer.from(value, 'hex'));
}

function toHex(value) {
  return Buffer.from(value).toString('hex');
}

