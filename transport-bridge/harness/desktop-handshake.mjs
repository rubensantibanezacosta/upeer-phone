#!/usr/bin/env node
import net from 'node:net';
import { randomBytes } from 'node:crypto';
import { ed25519 } from '@noble/curves/ed25519.js';
import { blake2b } from '@noble/hashes/blake2.js';

const target = required('UPEER_TARGET');
const port = Number(process.env.UPEER_PORT ?? 50005);
const seed = hex(process.env.UPEER_SEED_HEX ?? '0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20', 32);
const senderYggAddress = required('UPEER_SENDER_ADDRESS');
const alias = process.env.UPEER_ALIAS ?? 'Desktop harness';
const socksHost = process.env.UPEER_SOCKS_HOST ?? '127.0.0.1';
const socksPort = Number(process.env.UPEER_SOCKS_PORT ?? 9050);
const timeoutMillis = Number(process.env.UPEER_TIMEOUT_MS ?? 10000);

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
  alias,
  addresses: [senderYggAddress],
  isInternalSync: false,
  senderUpeerId,
  senderYggAddress,
};
const signature = ed25519.sign(new TextEncoder().encode(canonicalStringify(packet)), seed);
packet.signature = toHex(signature);
if (process.env.UPEER_TAMPER === '1') packet.alias = `${alias} tampered`;
const payload = Buffer.from(JSON.stringify(packet), 'utf8');
const frame = Buffer.alloc(payload.length + 4);
frame.writeUInt32BE(payload.length, 0);
payload.copy(frame, 4);

const socket = process.env.UPEER_DIRECT === '1'
  ? await directConnect(target, port, timeoutMillis)
  : await socksConnect(socksHost, socksPort, target, port, timeoutMillis);
await writeAndClose(socket, frame);
console.log(JSON.stringify({ target, port, senderUpeerId, type: packet.type, frameBytes: frame.length }));

function required(name) {
  const value = process.env[name];
  if (!value) throw new Error(`${name} is required`);
  return value;
}

function hex(value, expectedBytes) {
  if (!new RegExp(`^[0-9a-f]{${expectedBytes * 2}}$`, 'i').test(value)) {
    throw new Error(`Expected ${expectedBytes} bytes of hexadecimal`);
  }
  return Buffer.from(value, 'hex');
}

function toHex(value) {
  return Buffer.from(value).toString('hex');
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

function directConnect(host, targetPort, timeout) {
  return new Promise((resolve, reject) => {
    const connection = net.createConnection({ host, port: targetPort });
    connection.setTimeout(timeout, () => {
      connection.destroy();
      reject(new Error('Direct TCP timeout'));
    });
    connection.once('connect', () => {
      connection.setTimeout(0);
      resolve(connection);
    });
    connection.once('error', reject);
  });
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

function writeAndClose(connection, frame) {
  return new Promise((resolve, reject) => {
    connection.once('error', reject);
    connection.write(frame, (cause) => {
      if (cause) return reject(cause);
      connection.end();
      resolve();
    });
  });
}
