#!/usr/bin/env node
import net from 'node:net';
import { randomBytes } from 'node:crypto';
import { ed25519 } from '@noble/curves/ed25519.js';
import { blake2b } from '@noble/hashes/blake2.js';
import { canonicalStringify, frame, hex, required, socksConnect, toHex, writeAndClose } from './lib/network.mjs';

const target = required('UPEER_TARGET');
const senderYggAddress = required('UPEER_SENDER_ADDRESS');
const listenPort = Number(required('UPEER_LISTEN_PORT'));
const port = Number(process.env.UPEER_PORT ?? 50005);
const count = Number(process.env.UPEER_COUNT ?? 120);
const limit = Number(process.env.UPEER_LIMIT ?? 50);
const seed = hex(process.env.UPEER_SEED_HEX ?? '0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20', 32);
const socksHost = process.env.UPEER_SOCKS_HOST ?? '127.0.0.1';
const socksPort = Number(process.env.UPEER_SOCKS_PORT ?? 9051);
const timeoutMillis = Number(process.env.UPEER_TIMEOUT_MS ?? 60000);

const publicKey = ed25519.getPublicKey(seed);
const senderUpeerId = toHex(blake2b(publicKey, { dkLen: 32 }));
const packet = {
  type: 'HANDSHAKE_REQ',
  publicKey: toHex(publicKey),
  ephemeralPublicKey: toHex(randomBytes(32)),
  signedPreKey: {
    spkId: 1,
    spkPub: toHex(randomBytes(32)),
    spkSig: toHex(randomBytes(64)),
  },
  alias: 'Desktop rate limit',
  addresses: [senderYggAddress],
  isInternalSync: false,
  senderUpeerId,
  senderYggAddress,
};
packet.signature = toHex(ed25519.sign(new TextEncoder().encode(canonicalStringify(packet)), seed));
const requestFrame = frame(Buffer.from(JSON.stringify(packet), 'utf8'));

let accepted = 0;
let finished = false;
let elapsedMs = 0;
const server = startListener();
await waitForListener(server);

const startedAt = Date.now();
for (let index = 0; index < count; index += 1) {
  const connection = await socksConnect(socksHost, socksPort, target, port, timeoutMillis);
  await writeAndClose(connection, requestFrame);
}
elapsedMs = Date.now() - startedAt;

setTimeout(() => finish(), 6000).unref();

function startListener() {
  const listener = net.createServer((connection) => {
    let buffer = Buffer.alloc(0);
    connection.on('data', (chunk) => {
      buffer = Buffer.concat([buffer, chunk]);
      while (buffer.length >= 4) {
        const frameLength = buffer.readUInt32BE(0);
        if (frameLength <= 0 || frameLength > 10 * 1024 * 1024) return finish(new Error(`invalid frame length ${frameLength}`));
        if (buffer.length < frameLength + 4) return;
        const payload = buffer.subarray(4, frameLength + 4);
        buffer = buffer.subarray(frameLength + 4);
        try {
          handlePayload(payload);
        } catch (cause) {
          finish(cause);
        }
      }
    });
    connection.on('error', (cause) => finish(cause));
  });
  listener.on('error', (cause) => finish(cause));
  listener.listen(listenPort, '127.0.0.1');
  return listener;
}

function handlePayload(payload) {
  const response = JSON.parse(payload.toString('utf8'));
  if (response.type !== 'HANDSHAKE_ACCEPT') return;
  verifySigned(response, response.publicKey);
  accepted += 1;
}

function verifySigned(response, publicKeyHex) {
  if (typeof publicKeyHex !== 'string' || publicKeyHex.length !== 64) throw new Error('invalid publicKey');
  if (typeof response.signature !== 'string' || response.signature.length !== 128) throw new Error('invalid signature');
  const payload = { ...response };
  delete payload.signature;
  const canonical = canonicalStringify(payload);
  if (!ed25519.verify(Buffer.from(response.signature, 'hex'), new TextEncoder().encode(canonical), Buffer.from(publicKeyHex, 'hex'))) {
    throw new Error('invalid HANDSHAKE_ACCEPT signature');
  }
}

function waitForListener(listener) {
  return new Promise((resolve) => listener.once('listening', resolve));
}

function finish(cause) {
  if (finished) return;
  finished = true;
  server.closeAllConnections?.();
  server.close();
  if (cause) {
    console.error(cause.message);
    process.exitCode = 1;
    return;
  }
  const refill = Math.ceil((elapsedMs / 60_000) * limit);
  const expectedCeiling = limit + refill + 5;
  const verified = accepted >= limit && accepted < count && accepted <= expectedCeiling;
  console.log(JSON.stringify({ target, sent: count, accepted, limit, elapsedMs, expectedCeiling, verified }));
  if (!verified) process.exitCode = 1;
}
