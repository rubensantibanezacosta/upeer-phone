import { describe, expect, it } from 'vitest';

import { changedSections, hasUnreadIncoming, snapshotRefresh } from './chat-refresh';
import type { ProtocolMessage } from '../../modules/upeer-transport/src/UPeerTransport.types';

function message(overrides: Partial<ProtocolMessage> = {}): ProtocolMessage {
  return {
    id: 'm1',
    upeerId: 'a'.repeat(64),
    isMine: false,
    content: 'hola',
    status: 'delivered',
    timestamp: 1_793_000_000_000,
    ...overrides,
  };
}

describe('snapshotRefresh', () => {
  it('resume cada seccion por separado', () => {
    const snapshot = snapshotRefresh({ messages: [message()], contacts: [{ id: 1 }], conversations: [] });

    expect(Object.keys(snapshot)).toEqual(['messages', 'contacts', 'conversations']);
    expect(snapshot.messages).toContain('hola');
  });

  it('dos lecturas iguales producen la misma firma', () => {
    const first = snapshotRefresh({ messages: [message()], contacts: [], conversations: [] });
    const second = snapshotRefresh({ messages: [message()], contacts: [], conversations: [] });

    expect(changedSections(first, second).any).toBe(false);
  });
});

describe('changedSections', () => {
  it('sin lectura previa todo cuenta como cambio', () => {
    const next = snapshotRefresh({ messages: [], contacts: [], conversations: [] });

    expect(changedSections(null, next)).toEqual({ messages: true, contacts: true, conversations: true, any: true });
  });

  it('detecta solo la seccion que cambio', () => {
    const base = snapshotRefresh({ messages: [message()], contacts: [], conversations: [] });
    const next = snapshotRefresh({ messages: [message({ status: 'read' })], contacts: [], conversations: [] });

    expect(changedSections(base, next)).toEqual({ messages: true, contacts: false, conversations: false, any: true });
  });

  it('detecta un mensaje nuevo', () => {
    const base = snapshotRefresh({ messages: [message()], contacts: [], conversations: [] });
    const next = snapshotRefresh({ messages: [message({ id: 'm2' }), message()], contacts: [], conversations: [] });

    expect(changedSections(base, next).messages).toBe(true);
  });

  it('no confunde un contacto nuevo con mensajes nuevos', () => {
    const base = snapshotRefresh({ messages: [], contacts: [], conversations: [] });
    const next = snapshotRefresh({ messages: [], contacts: [{ id: 2 }], conversations: [] });

    expect(changedSections(base, next)).toEqual({ messages: false, contacts: true, conversations: false, any: true });
  });
});

describe('hasUnreadIncoming', () => {
  it('pide marcar leido solo si hay algo entrante sin leer', () => {
    expect(hasUnreadIncoming([message()])).toBe(true);
    expect(hasUnreadIncoming([message({ status: 'read' })])).toBe(false);
    expect(hasUnreadIncoming([message({ isMine: true, status: 'delivered' })])).toBe(false);
    expect(hasUnreadIncoming([])).toBe(false);
  });

  it('no cuenta un mensaje entrante ya leido aunque haya otros mios', () => {
    expect(hasUnreadIncoming([message({ isMine: true, status: 'sent' }), message({ status: 'read' })])).toBe(false);
  });
});
