import crypto from 'node:crypto';
import { gunzipSync } from 'node:zlib';
import sodium from 'libsodium-wrappers';

export function hkdf(rootKey, inputMaterial, info) {
  const prk = sodium.crypto_generichash(32, inputMaterial, rootKey);
  return [
    sodium.crypto_kdf_derive_from_key(32, 1, info, prk),
    sodium.crypto_kdf_derive_from_key(32, 2, info, prk),
  ];
}

export function chainStep(chainKey) {
  const messageKey = crypto.createHmac('sha256', chainKey).update(Buffer.from([1])).digest();
  const nextChainKey = crypto.createHmac('sha256', chainKey).update(Buffer.from([2])).digest();
  return [new Uint8Array(messageKey), new Uint8Array(nextChainKey)];
}

export function concat(...values) {
  const result = new Uint8Array(values.reduce((total, value) => total + value.length, 0));
  let offset = 0;
  for (const value of values) {
    result.set(value, offset);
    offset += value.length;
  }
  return result;
}

export function decompress(text) {
  if (!text.startsWith('\u2763UP\u2763')) return text;
  try {
    return gunzipSync(Buffer.from(text.slice(4), 'base64')).toString('utf8');
  } catch {
    return text;
  }
}

export function createResponderState(sharedSecret, signedPreKey) {
  return {
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
  };
}

export function ratchetDecrypt(state, header, ciphertextHex, nonceHex) {
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
  const dh2 = sodium.crypto_scalarmult(nextKeyPair.privateKey, remotePublicKey);
  [state.rk, state.cks] = hkdf(state.rk, dh2, 'ratchet2');
}

function skipKeys(state, until) {
  if (!state.ckr) return;
  while (state.nr < until) {
    const [messageKey, nextChainKey] = chainStep(state.ckr);
    state.skipped.set(`${Buffer.from(state.dhr).toString('hex')}:${state.nr}`, messageKey);
    state.ckr = nextChainKey;
    state.nr += 1;
  }
}
