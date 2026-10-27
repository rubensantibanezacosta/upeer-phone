export type ContactCard = {
  name: string;
  address: string;
  upeerId: string;
  publicKey?: string;
  avatar?: string;
  text?: string;
};

const MAX_NAME_LENGTH = 100;
// El CHAT nativo rechaza más de 256 KB, así que la tarjeta no puede llevar avatares grandes.
const MAX_AVATAR_LENGTH = 64 * 1024;
const UPEER_ID = /^[0-9a-f]{64}$/i;

export const CONTACT_CARD_SUMMARY = 'Tarjeta de contacto';

export function serializeContactCard(card: ContactCard): string {
  return JSON.stringify({
    type: 'contact_card',
    text: '',
    contact: {
      name: card.name,
      address: card.address,
      upeerId: card.upeerId,
      publicKey: card.publicKey ?? '',
      avatar: card.avatar,
    },
  });
}

export function parseContactCard(content: string): ContactCard | null {
  if (!content.startsWith('{') || !content.endsWith('}')) return null;
  let parsed: unknown;
  try {
    parsed = JSON.parse(content);
  } catch {
    return null;
  }
  if (!parsed || typeof parsed !== 'object') return null;
  const payload = parsed as { type?: unknown; text?: unknown; contact?: unknown };
  if (payload.type !== 'contact_card') return null;
  const contact = (payload.contact ?? {}) as Record<string, unknown>;
  const upeerId = typeof contact.upeerId === 'string' ? contact.upeerId : '';
  if (!UPEER_ID.test(upeerId)) return null;
  const name = typeof contact.name === 'string' ? contact.name.slice(0, MAX_NAME_LENGTH) : '';
  const address = typeof contact.address === 'string' ? contact.address.slice(0, MAX_NAME_LENGTH) : '';
  const publicKey = typeof contact.publicKey === 'string' && contact.publicKey.length === 64 ? contact.publicKey : undefined;
  const avatar = typeof contact.avatar === 'string' && contact.avatar.startsWith('data:image/') && contact.avatar.length <= MAX_AVATAR_LENGTH
    ? contact.avatar
    : undefined;
  const text = typeof payload.text === 'string' && payload.text.trim() ? payload.text.trim().slice(0, MAX_NAME_LENGTH) : undefined;
  return { name, address, upeerId, publicKey, avatar, text };
}
