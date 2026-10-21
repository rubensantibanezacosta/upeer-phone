import { describe, expect, it } from 'vitest';
import { decodeAvatarData } from './avatar-data';

describe('profile avatar validation', () => {
  it('decodes a complete JPEG data URI', () => {
    expect(decodeAvatarData('data:image/jpeg;base64,/9j/2Q==')).toEqual(new Uint8Array([255, 216, 255, 217]));
  });

  it('rejects non-JPEG data URIs', () => {
    expect(() => decodeAvatarData('data:image/png;base64,iVBORw0KGgo=')).toThrow('JPEG válido');
  });

  it('rejects invalid base64 padding', () => {
    expect(() => decodeAvatarData('data:image/jpeg;base64,/9j/2Q=')).toThrow('codificación inválida');
  });

  it('rejects truncated or invalid JPEG content', () => {
    expect(() => decodeAvatarData('data:image/jpeg;base64,AAAA')).toThrow('JPEG completo válido');
  });
});