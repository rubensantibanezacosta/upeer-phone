import { describe, expect, it } from 'vitest';

import { countUnreadChats, formatBadgeCount, MAX_BADGE_COUNT } from './chat-unread';

function conversation(overrides: { unread?: number; lastIsMine?: boolean }): Parameters<typeof countUnreadChats>[0][number] {
  return {
    alias: '',
    lastIsMine: overrides.lastIsMine ?? false,
    lastMessage: 'hola',
    lastStatus: 'delivered',
    lastTimestamp: 0,
    unread: overrides.unread ?? 0,
    upeerId: 'a'.repeat(64),
  };
}

describe('countUnreadChats', () => {
  it('cuenta las conversaciones con mensajes recibidos sin leer', () => {
    expect(countUnreadChats([conversation({ unread: 2 }), conversation({ unread: 0 }), conversation({ unread: 1 })])).toBe(2);
  });

  it('no cuenta una conversación cuyo último mensaje es propio', () => {
    expect(countUnreadChats([conversation({ unread: 3, lastIsMine: true })])).toBe(0);
  });

  it('trata un valor ausente como cero', () => {
    expect(countUnreadChats([conversation({})])).toBe(0);
  });

  it('devuelve cero sin conversaciones', () => {
    expect(countUnreadChats([])).toBe(0);
  });
});

describe('formatBadgeCount', () => {
  it('oculta el contador cuando no hay nada sin leer', () => {
    expect(formatBadgeCount(0)).toBeUndefined();
    expect(formatBadgeCount(-3)).toBeUndefined();
  });

  it('escribe el número tal cual hasta el límite', () => {
    expect(formatBadgeCount(1)).toBe('1');
    expect(formatBadgeCount(MAX_BADGE_COUNT)).toBe('99');
  });

  it('recorta por encima del límite como escritorio', () => {
    expect(formatBadgeCount(MAX_BADGE_COUNT + 1)).toBe('99+');
  });
});
