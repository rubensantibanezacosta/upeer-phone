#!/usr/bin/env node
import { frame, required, socksConnect } from './lib/network.mjs';

const target = required('UPEER_TARGET');
const port = Number(process.env.UPEER_PORT ?? 50005);
const socksHost = process.env.UPEER_SOCKS_HOST ?? '127.0.0.1';
const socksPort = Number(process.env.UPEER_SOCKS_PORT ?? 9051);
const timeoutMillis = Number(process.env.UPEER_TIMEOUT_MS ?? 10000);
const payload = Buffer.from(JSON.stringify({
  type: 'HANDSHAKE_REQ',
  publicKey: '00'.repeat(32),
  signedPreKey: { spkId: 1, spkPub: '00'.repeat(32), spkSig: '00'.repeat(64) },
  alias: 'truncated frame',
  isInternalSync: false,
  senderUpeerId: '00'.repeat(32),
  senderYggAddress: process.env.UPEER_SENDER_ADDRESS ?? target,
}), 'utf8');
const encoded = frame(payload);
const cut = Math.max(1, Math.min(encoded.length - 1, Number(process.env.UPEER_TRUNCATE_BYTES ?? 6)));
const connection = await socksConnect(socksHost, socksPort, target, port, timeoutMillis);
await new Promise((resolve, reject) => {
  connection.once('error', reject);
  connection.end(encoded.subarray(0, cut), resolve);
});
console.log(JSON.stringify({ type: 'TRUNCATED_FRAME', target, port, bytes: cut, frameBytes: encoded.length }));
