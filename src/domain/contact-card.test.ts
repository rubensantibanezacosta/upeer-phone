import { describe, expect, it } from 'vitest';

import { parseContactCard, serializeContactCard } from './contact-card';

const ID = 'a'.repeat(64);
const KEY = 'b'.repeat(64);

describe('serializeContactCard', () => {
  it('emits the desktop wire format', () => {
    const content = serializeContactCard({ name: 'Ana', address: '200::1', publicKey: KEY, upeerId: ID });
    expect(JSON.parse(content)).toEqual({
      type: 'contact_card',
      text: '',
      contact: { name: 'Ana', address: '200::1', publicKey: KEY, upeerId: ID },
    });
  });
});

describe('parseContactCard', () => {
  it('round-trips a card', () => {
    const content = serializeContactCard({ name: 'Ana', address: '200::1', publicKey: KEY, upeerId: ID });
    expect(parseContactCard(content)).toMatchObject({ name: 'Ana', address: '200::1', publicKey: KEY, upeerId: ID });
  });

  it('rejects plain text', () => {
    expect(parseContactCard('hola')).toBeNull();
  });

  it('rejects malformed json', () => {
    expect(parseContactCard('{no json}')).toBeNull();
  });

  it('rejects another json payload type', () => {
    expect(parseContactCard(JSON.stringify({ type: 'file', contact: { upeerId: ID } }))).toBeNull();
  });

  it('rejects an invalid contact identity', () => {
    expect(parseContactCard(JSON.stringify({ type: 'contact_card', contact: { upeerId: 'nope' } }))).toBeNull();
  });

  it('drops an avatar that is not a data image', () => {
    const content = JSON.stringify({ type: 'contact_card', contact: { upeerId: ID, avatar: 'http://x/y.png' } });
    expect(parseContactCard(content)?.avatar).toBeUndefined();
  });

  it('keeps a valid data image avatar', () => {
    const content = JSON.stringify({ type: 'contact_card', contact: { upeerId: ID, avatar: 'data:image/png;base64,AA' } });
    expect(parseContactCard(content)?.avatar).toBe('data:image/png;base64,AA');
  });

  it('drops an avatar that would not fit in a native message', () => {
    const avatar = `data:image/png;base64,${'A'.repeat(64 * 1024)}`;
    const content = JSON.stringify({ type: 'contact_card', contact: { upeerId: ID, avatar } });
    expect(parseContactCard(content)?.avatar).toBeUndefined();
  });

  it('drops a public key with the wrong length', () => {
    const content = JSON.stringify({ type: 'contact_card', contact: { upeerId: ID, publicKey: 'abc' } });
    expect(parseContactCard(content)?.publicKey).toBeUndefined();
  });
});
