export const MAX_AVATAR_BYTES = 1_500_000;

export function decodeAvatarData(value: string): Uint8Array {
  const match = /^data:image\/jpeg;base64,([A-Za-z0-9+/]+={0,2})$/.exec(value);
  if (!match) throw new Error('La foto de perfil debe estar codificada como JPEG válido.');
  const encoded = match[1];
  if (encoded.length % 4 !== 0 || /=/.test(encoded.slice(0, -2))) {
    throw new Error('La foto de perfil tiene una codificación inválida.');
  }
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
  const padding = encoded.endsWith('==') ? 2 : encoded.endsWith('=') ? 1 : 0;
  const bytes = new Uint8Array(encoded.length / 4 * 3 - padding);
  let accumulator = 0;
  let bits = 0;
  let offset = 0;
  for (const character of encoded.slice(0, encoded.length - padding || undefined)) {
    const digit = alphabet.indexOf(character);
    if (digit < 0) throw new Error('La foto de perfil tiene una codificación inválida.');
    accumulator = (accumulator << 6) | digit;
    bits += 6;
    if (bits >= 8) {
      bits -= 8;
      bytes[offset++] = (accumulator >> bits) & 0xff;
    }
  }
  if (offset !== bytes.length) throw new Error('La foto de perfil tiene una codificación inválida.');
  if (bytes.length > MAX_AVATAR_BYTES) throw new Error('La foto de perfil supera el límite de 1,5 MB.');
  if (bytes.length < 4 || bytes[0] !== 0xff || bytes[1] !== 0xd8 || bytes.at(-2) !== 0xff || bytes.at(-1) !== 0xd9) {
    throw new Error('La foto seleccionada no es un JPEG completo válido.');
  }
  return bytes;
}