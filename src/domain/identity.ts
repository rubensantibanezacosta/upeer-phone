import { deriveMnemonicIdentity } from '../../modules/upeer-transport/src/UPeerTransportModule';
import { entropyToMnemonic, validateMnemonic } from './mnemonic';

export type UPeerIdentity = {
  mnemonic: string;
  upeerId: string;
  publicKey: string;
  secretKey: string;
};

const encoder = new TextEncoder();

export function normalizeMnemonic(mnemonic: string): string {
  return mnemonic.normalize('NFKD').trim().toLowerCase().split(/\s+/).join(' ');
}

export function createMnemonic(): string {
  const entropy = new Uint8Array(16);
  globalThis.crypto.getRandomValues(entropy);
  return entropyToMnemonic(entropy);
}

export async function deriveIdentity(mnemonicInput: string): Promise<UPeerIdentity> {
  const mnemonic = normalizeMnemonic(mnemonicInput);
  if (!validateMnemonic(mnemonic)) {
    throw new Error('La frase de recuperación no es válida. Comprueba las 12 palabras y su orden.');
  }
  const keys = await deriveMnemonicIdentity(mnemonic);
  return { mnemonic, ...keys };
}

export function canonicalStringify(value: unknown): string {
  if (value === null || typeof value !== 'object') {
    return JSON.stringify(value);
  }
  if (Array.isArray(value)) {
    return `[${value.map(canonicalStringify).join(',')}]`;
  }
  const entries = Object.entries(value as Record<string, unknown>)
    .filter(([, entry]) => entry !== undefined)
    .sort(([left], [right]) => left.localeCompare(right));
  return `{${entries.map(([key, entry]) => `${JSON.stringify(key)}:${canonicalStringify(entry)}`).join(',')}}`;
}

export function fromHex(value: string): Uint8Array {
  if (!/^(?:[0-9a-f]{2})+$/i.test(value)) {
    throw new Error('La clave contiene hexadecimal inválido.');
  }
  return Uint8Array.from(value.match(/.{2}/g) ?? [], (byte) => Number.parseInt(byte, 16));
}

export function toHex(value: Uint8Array): string {
  return Array.from(value, (byte) => byte.toString(16).padStart(2, '0')).join('');
}

export function bytesToBase64(value: Uint8Array): string {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
  let result = '';
  for (let index = 0; index < value.length; index += 3) {
    const first = value[index];
    const second = value[index + 1];
    const third = value[index + 2];
    result += alphabet[first >> 2];
    result += alphabet[((first & 3) << 4) | ((second ?? 0) >> 4)];
    result += second === undefined ? '=' : alphabet[((second & 15) << 2) | ((third ?? 0) >> 6)];
    result += third === undefined ? '=' : alphabet[third & 63];
  }
  return result;
}