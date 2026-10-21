import { describe, expect, it } from 'vitest';
import { entropyToMnemonic, validateMnemonic } from './mnemonic';

describe('BIP39 mnemonic helpers', () => {
  it('matches the standard zero-entropy English vector', () => {
    const phrase = entropyToMnemonic(new Uint8Array(16));

    expect(phrase).toBe('abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about');
    expect(validateMnemonic(phrase)).toBe(true);
  });

  it('rejects a phrase with an invalid checksum', () => {
    expect(validateMnemonic('abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon')).toBe(false);
  });
});