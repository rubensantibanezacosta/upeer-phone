import { describe, expect, it } from 'vitest';

import { decodeEnrollmentTransfer, encodeEnrollmentTransfer } from './enrollment-transfer';

const sealed = {
  inviteId: 'ab'.repeat(32),
  nonce: 'cd'.repeat(12),
  ciphertext: 'ef'.repeat(80),
};

describe('enrollment transfer codec', () => {
  it('round-trips a sealed message through base64url', () => {
    const code = encodeEnrollmentTransfer(sealed);

    expect(code).toMatch(/^[A-Za-z0-9_-]+$/);
    expect(decodeEnrollmentTransfer(code)).toEqual(sealed);
  });

  it('matches the RFC 4648 base64url encoding of the same bytes', () => {
    expect(encodeEnrollmentTransfer({})).toBe('e30');
    expect(decodeEnrollmentTransfer('e30')).toEqual({});
  });

  it('tolerates whitespace introduced by clipboard or chat clients', () => {
    const code = encodeEnrollmentTransfer(sealed);

    expect(decodeEnrollmentTransfer(`${code.slice(0, 20)}\n ${code.slice(20)}`)).toEqual(sealed);
  });

  it('rejects codes outside the base64url alphabet', () => {
    expect(decodeEnrollmentTransfer('abc+def')).toBeNull();
    expect(decodeEnrollmentTransfer('abc/def')).toBeNull();
    expect(decodeEnrollmentTransfer('abc=def')).toBeNull();
  });

  it('rejects empty, oversized and non-JSON payloads', () => {
    expect(decodeEnrollmentTransfer('')).toBeNull();
    expect(decodeEnrollmentTransfer('   ')).toBeNull();
    expect(decodeEnrollmentTransfer('a'.repeat(5000))).toBeNull();
    expect(decodeEnrollmentTransfer(encodeEnrollmentTransfer(sealed).slice(0, 4))).toBeNull();
  });

  it('rejects a payload that decodes to something other than an object', () => {
    expect(decodeEnrollmentTransfer('aGVsbG8')).toBeNull();
  });
});
