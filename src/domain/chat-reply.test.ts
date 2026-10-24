import { describe, expect, it } from 'vitest';

import { buildReplyPreview, replyExcerpt, REPLY_EXCERPT_LENGTH } from './chat-reply';
import type { ProtocolMessage } from '../../modules/upeer-transport/src/UPeerTransport.types';

const PEER = 'b'.repeat(64);

function message(patch: Partial<ProtocolMessage>): ProtocolMessage {
  return { content: 'hola', id: 'id-1', isMine: false, status: 'delivered', timestamp: 1, upeerId: PEER, ...patch };
}

describe('replyExcerpt', () => {
  it('colapsa espacios y saltos de línea', () => {
    expect(replyExcerpt('  uno\n\n dos   tres ')).toBe('uno dos tres');
  });

  it('recorta con puntos suspensivos', () => {
    const excerpt = replyExcerpt('a'.repeat(REPLY_EXCERPT_LENGTH + 20));
    expect(excerpt.length).toBe(REPLY_EXCERPT_LENGTH);
    expect(excerpt.endsWith('…')).toBe(true);
  });

  it('resume una tarjeta de contacto', () => {
    const card = JSON.stringify({ contact: { address: '', name: 'Ana', upeerId: PEER }, text: '', type: 'contact_card' });
    expect(replyExcerpt(card)).toBe('Tarjeta de contacto: Ana');
  });
});

describe('buildReplyPreview', () => {
  it('usa la etiqueta del autor', () => {
    expect(buildReplyPreview(message({ content: 'qué tal' }), { mine: 'Tú', peer: 'Ana' })).toEqual({ author: 'Ana', excerpt: 'qué tal' });
    expect(buildReplyPreview(message({ isMine: true }), { mine: 'Tú', peer: 'Ana' })?.author).toBe('Tú');
  });

  it('devuelve null si el mensaje citado no está cargado', () => {
    expect(buildReplyPreview(undefined, { mine: 'Tú', peer: 'Ana' })).toBeNull();
  });
});
