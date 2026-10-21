import { sha256 } from '@noble/hashes/sha2.js';
import englishWordsJson from '../assets/bip39-english-list.js';

const englishWords = englishWordsJson as string[];

export function entropyToMnemonic(entropy: Uint8Array): string {
  if (entropy.length !== 16) {
    throw new Error('La entropía BIP39 debe tener 128 bits.');
  }

  const entropyBits = Array.from(entropy, (byte) => byte.toString(2).padStart(8, '0')).join('');
  const checksumBits = sha256(entropy).slice(0, 1)[0].toString(2).padStart(8, '0').slice(0, 4);
  const bits = entropyBits + checksumBits;
  const words: string[] = [];

  for (let offset = 0; offset < bits.length; offset += 11) {
    const index = Number.parseInt(bits.slice(offset, offset + 11), 2);
    words.push(englishWords[index]);
  }

  return words.join(' ');
}

export function validateMnemonic(mnemonic: string): boolean {
  const words = mnemonic.normalize('NFKD').trim().split(/\s+/);
  if (words.length !== 12) {
    return false;
  }

  let bits = '';
  for (const word of words) {
    const index = englishWords.indexOf(word);
    if (index < 0) {
      return false;
    }
    bits += index.toString(2).padStart(11, '0');
  }

  const entropyBits = bits.slice(0, 128);
  const checksumBits = bits.slice(128);
  const entropy = new Uint8Array(16);
  for (let offset = 0; offset < entropyBits.length; offset += 8) {
    entropy[offset / 8] = Number.parseInt(entropyBits.slice(offset, offset + 8), 2);
  }

  const expectedChecksum = sha256(entropy)[0].toString(2).padStart(8, '0').slice(0, 4);
  return checksumBits === expectedChecksum;
}