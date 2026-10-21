#!/usr/bin/env node
import net from 'node:net';
import { ed25519 } from '@noble/curves/ed25519.js';
import { blake2b } from '@noble/hashes/blake2.js';

const port = Number(process.env.UPEER_LISTEN_PORT ?? 50105);
const expectedSenderAddress = required('UPEER_EXPECT_SENDER_ADDRESS');
const expectedType = process.env.UPEER_EXPECT_TYPE ?? 'HANDSHAKE_ACCEPT';
const expectedPublicKey = process.env.UPEER_EXPECT_PUBLIC_KEY;
const timeoutMillis = Number(process.env.UPEER_TIMEOUT_MS ?? 30000);
let settled = false;

const server = net.createServer((connection) => {
  let buffer = Buffer.alloc(0);
  connection.on('data', (chunk) => {
    buffer = Buffer.concat([buffer, chunk]);
    if (buffer.length < 4) return;
    const frameLength = buffer.readUInt32BE(0);
    if (frameLength <= 0 || frameLength > 10 * 1024 * 1024) {
      fail(new Error(`invalid frame length ${frameLength}`));
      return;
    }
    if (buffer.length < frameLength + 4) return;
    const payload = buffer.subarray(4, frameLength + 4);
    buffer = buffer.subarray(frameLength + 4);
    try {
      if (verifyPayload(payload)) succeed(payload.length);
    } catch (cause) {
      fail(cause);
    }
  });
  connection.on('error', fail);
});

server.on('error', fail);
server.listen(port, '127.0.0.1', () => {
  console.log(JSON.stringify({ listening: true, port }));
});
setTimeout(() => fail(new Error('timeout waiting for HANDSHAKE_ACCEPT')), timeoutMillis).unref();

function verifyPayload(payload) {
  const packet = JSON.parse(payload.toString('utf8'));
  if (packet.type !== expectedType) {
    console.log(JSON.stringify({ ignored: packet.type }));
    return false;
  }
  if (packet.senderYggAddress !== expectedSenderAddress) throw new Error('senderYggAddress mismatch');
  if (packet.isInternalSync !== false) throw new Error('isInternalSync must be false');
  const publicKeyHex = expectedType === 'ACK' ? expectedPublicKey : packet.publicKey;
  if (typeof publicKeyHex !== 'string' || publicKeyHex.length !== 64) throw new Error('invalid publicKey');
  if (typeof packet.signature !== 'string' || packet.signature.length !== 128) throw new Error('invalid signature');
  const publicKey = Buffer.from(publicKeyHex, 'hex');
  const derivedId = Buffer.from(blake2b(publicKey, { dkLen: 32 })).toString('hex');
  if (packet.senderUpeerId !== derivedId) throw new Error('senderUpeerId mismatch');
  const signedData = { ...packet };
  delete signedData.signature;
  delete signedData.senderUpeerId;
  delete signedData.senderYggAddress;
  const signaturePayload = { ...signedData, senderUpeerId: packet.senderUpeerId, senderYggAddress: packet.senderYggAddress };
  if (!ed25519.verify(Buffer.from(packet.signature, 'hex'), new TextEncoder().encode(canonicalStringify(signaturePayload)), publicKey)) {
    throw new Error('invalid signed packet signature');
  }
  if (expectedType === 'ACK') {
    if (typeof packet.id !== 'string' || !packet.id) throw new Error('invalid ACK id');
    if (packet.status !== 'delivered' && packet.status !== 'read') throw new Error('invalid ACK status');
    console.log(JSON.stringify({
      type: packet.type,
      publicKey: publicKeyHex,
      senderUpeerId: packet.senderUpeerId,
      senderYggAddress: packet.senderYggAddress,
      id: packet.id,
      status: packet.status,
    }));
    return true;
  }
  const signedPreKey = packet.signedPreKey;
  if (!signedPreKey || typeof signedPreKey.spkPub !== 'string' || signedPreKey.spkPub.length !== 64) throw new Error('invalid signedPreKey.spkPub');
  if (typeof signedPreKey.spkSig !== 'string' || signedPreKey.spkSig.length !== 128) throw new Error('invalid signedPreKey.spkSig');
  if (typeof signedPreKey.spkId !== 'number' || signedPreKey.spkId <= 0) throw new Error('invalid signedPreKey.spkId');
  if (Object.prototype.hasOwnProperty.call(signedPreKey, 'spkSk')) throw new Error('signed pre-key secret leaked');
  if (!ed25519.verify(Buffer.from(signedPreKey.spkSig, 'hex'), Buffer.from(signedPreKey.spkPub, 'hex'), publicKey)) throw new Error('invalid signed pre-key signature');
  console.log(JSON.stringify({
    type: packet.type,
    publicKey: packet.publicKey,
    senderUpeerId: packet.senderUpeerId,
    senderYggAddress: packet.senderYggAddress,
    spkId: signedPreKey.spkId,
    spkPub: signedPreKey.spkPub,
    alias: packet.alias ?? null,
  }));
  return true;
}

function succeed(packetBytes) {
  if (settled) return;
  settled = true;
  console.log(JSON.stringify({ verified: true, packetBytes }));
  server.close();
  setTimeout(() => process.exit(0), 10).unref();
}

function fail(cause) {
  if (settled) return;
  settled = true;
  console.error(JSON.stringify({ verified: false, error: cause.message }));
  server.close();
  setTimeout(() => process.exit(1), 10).unref();
}

function required(name) {
  const value = process.env[name];
  if (!value) throw new Error(`${name} is required`);
  return value;
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
