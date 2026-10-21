#!/usr/bin/env node
import net from 'node:net';
import crypto from 'node:crypto';
import sodium, { ready } from 'libsodium-wrappers';

await ready;

const target = required('UPEER_TARGET');
const port = Number(process.env.UPEER_PORT ?? 50005);
const senderYggAddress = required('UPEER_SENDER_ADDRESS');
const senderSeed = hex(process.env.UPEER_SEED_HEX ?? '0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20', 32);
const mobilePublicKey = hex(required('UPEER_MOBILE_PUBLIC_KEY'), 32);
const mobileSpkPublicKey = hex(required('UPEER_MOBILE_SPK_PUB'), 32);
const mobileSpkId = Number(required('UPEER_MOBILE_SPK_ID'));
const message = process.env.UPEER_MESSAGE ?? 'Hola desde Desktop por Yggdrasil';
const socksHost = process.env.UPEER_SOCKS_HOST ?? '127.0.0.1';
const socksPort = Number(process.env.UPEER_SOCKS_PORT ?? 9051);
const timeoutMillis = Number(process.env.UPEER_TIMEOUT_MS ?? 15000);

const senderKeys = sodium.crypto_sign_seed_keypair(senderSeed);
const senderUpeerId = toHex(sodium.crypto_generichash(32, senderKeys.publicKey));
const senderCurveSecret = sodium.crypto_sign_ed25519_sk_to_curve25519(senderKeys.privateKey);
const mobileCurvePublic = sodium.crypto_sign_ed25519_pk_to_curve25519(mobilePublicKey);
const ephemeral = sodium.crypto_box_keypair();
const aliceDhSecret = sodium.crypto_box_keypair();

const dh1 = sodium.crypto_scalarmult(senderCurveSecret, mobileSpkPublicKey);
const dh2 = sodium.crypto_scalarmult(ephemeral.privateKey, mobileCurvePublic);
const dh3 = sodium.crypto_scalarmult(ephemeral.privateKey, mobileSpkPublicKey);
const [sharedSecret] = hkdf(new Uint8Array(32), concat(dh1, dh2, dh3), 'X3DHv1.0');
const dhRatchet = sodium.crypto_scalarmult(aliceDhSecret.privateKey, mobileSpkPublicKey);
const [, initialSendChain] = hkdf(sharedSecret, dhRatchet, 'ratchet1');
const messageCount = Number(process.env.UPEER_MESSAGE_COUNT ?? 2);

const handshake = {
  type: 'HANDSHAKE_REQ',
  publicKey: toHex(senderKeys.publicKey),
  ephemeralPublicKey: toHex(sodium.randombytes_buf(32)),
  signedPreKey: {
    spkId: Date.now(),
    spkPub: toHex(sodium.randombytes_buf(32)),
    spkSig: toHex(sodium.randombytes_buf(64)),
  },
  alias: 'Desktop sealed chat',
  addresses: [senderYggAddress],
  isInternalSync: false,
  senderUpeerId,
  senderYggAddress,
};
handshake.signature = toHex(sodium.crypto_sign_detached(new TextEncoder().encode(canonicalStringify(handshake)), senderKeys.privateKey));

let sendChain = initialSendChain;
const chats = [];
const chatFrames = [];
for (let index = 0; index < messageCount; index += 1) {
  const [messageKey, nextSendChain] = chainStep(sendChain);
  sendChain = nextSendChain;
  const nonce = sodium.randombytes_buf(24);
  const ciphertext = sodium.crypto_secretbox_easy(new TextEncoder().encode(message), nonce, messageKey);
  const chat = {
    type: 'CHAT',
    id: crypto.randomUUID(),
    timestamp: Date.now(),
    content: toHex(ciphertext),
    nonce: toHex(nonce),
    ratchetHeader: {
      dh: toHex(aliceDhSecret.publicKey),
      pn: 0,
      n: index,
    },
    ...(index === 0 ? {
      x3dhInit: {
        ekPub: toHex(ephemeral.publicKey),
        spkId: mobileSpkId,
        ikPub: toHex(senderKeys.publicKey),
      },
    } : {}),
    isInternalSync: false,
    senderUpeerId,
    senderYggAddress,
  };
  chat.signature = toHex(sodium.crypto_sign_detached(new TextEncoder().encode(canonicalStringify(chat)), senderKeys.privateKey));
  const sealed = sodium.crypto_box_seal(new TextEncoder().encode(JSON.stringify(chat)), mobileCurvePublic);
  chats.push({ id: chat.id, ciphertextBytes: ciphertext.length, sealedBytes: sealed.length });
  chatFrames.push(frame(Buffer.from(JSON.stringify({ type: 'SEALED', ciphertext: toHex(sealed) }), 'utf8')));
}

const handshakeFrame = frame(Buffer.from(JSON.stringify(handshake), 'utf8'));
const handshakeConnection = await socksConnect(socksHost, socksPort, target, port, timeoutMillis);
await writeAndClose(handshakeConnection, handshakeFrame);
await sleep(1200);
const chatConnection = await socksConnect(socksHost, socksPort, target, port, timeoutMillis);
await writeAndClose(chatConnection, Buffer.concat(chatFrames));
console.log(JSON.stringify({
  target,
  senderUpeerId,
  messages: chats,
  message,
  mobileSpkId,
  handshakeFrameBytes: handshakeFrame.length,
  chatFrameBytes: chatFrames.reduce((total, value) => total + value.length, 0),
}));

function hkdf(rootKey, inputMaterial, info) {
  const prk = sodium.crypto_generichash(32, inputMaterial, rootKey);
  return [
    sodium.crypto_kdf_derive_from_key(32, 1, info, prk),
    sodium.crypto_kdf_derive_from_key(32, 2, info, prk),
  ];
}

function chainStep(chainKey) {
  const messageKey = crypto.createHmac('sha256', chainKey).update(Buffer.from([1])).digest();
  const nextChainKey = crypto.createHmac('sha256', chainKey).update(Buffer.from([2])).digest();
  return [new Uint8Array(messageKey), new Uint8Array(nextChainKey)];
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
  const total = values.reduce((sum, value) => sum + value.length, 0);
  const result = new Uint8Array(total);
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

function sleep(milliseconds) {
  return new Promise((resolve) => setTimeout(resolve, milliseconds));
}

function required(name) {
  const value = process.env[name];
  if (!value) throw new Error(`${name} is required`);
  return value;
}

function hex(value, expectedBytes) {
  if (!new RegExp(`^[0-9a-f]{${expectedBytes * 2}}$`, 'i').test(value)) {
    throw new Error(`Expected ${expectedBytes} bytes of hexadecimal`);
  }
  return new Uint8Array(Buffer.from(value, 'hex'));
}

function toHex(value) {
  return Buffer.from(value).toString('hex');
}
