const ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_';
const MAX_TRANSFER_LENGTH = 4096;
const LOOKUP: Record<string, number> = Object.fromEntries(Array.from(ALPHABET, (character, index) => [character, index]));

export function encodeEnrollmentTransfer(value: unknown): string {
  const bytes = new TextEncoder().encode(JSON.stringify(value));
  let output = '';
  for (let offset = 0; offset < bytes.length; offset += 3) {
    const chunk = (bytes[offset] << 16) | ((bytes[offset + 1] ?? 0) << 8) | (bytes[offset + 2] ?? 0);
    const available = Math.min(3, bytes.length - offset);
    for (let index = 0; index < available + 1; index += 1) {
      output += ALPHABET[(chunk >> (18 - 6 * index)) & 0x3f];
    }
  }
  return output;
}

export function decodeEnrollmentTransfer(code: string): unknown | null {
  const normalized = code.replace(/\s+/g, '');
  if (normalized.length === 0 || normalized.length > MAX_TRANSFER_LENGTH) return null;
  const bytes = new Uint8Array(Math.floor(normalized.length * 3 / 4));
  let accumulator = 0;
  let bits = 0;
  let offset = 0;
  for (const character of normalized) {
    const digit = LOOKUP[character];
    if (digit === undefined) return null;
    accumulator = ((accumulator << 6) | digit) & 0xffffff;
    bits += 6;
    if (bits >= 8) {
      bits -= 8;
      bytes[offset++] = (accumulator >> bits) & 0xff;
      accumulator &= (1 << bits) - 1;
    }
  }
  if (offset === 0) return null;
  try {
    return JSON.parse(new TextDecoder().decode(bytes.subarray(0, offset)));
  } catch {
    return null;
  }
}
