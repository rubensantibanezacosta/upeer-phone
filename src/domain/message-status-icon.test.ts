import { describe, expect, it } from 'vitest';

import { statusIconIsRead, statusIconKind } from './message-status-icon';
import type { ProtocolMessage } from '../../modules/upeer-transport/src/UPeerTransport.types';

describe('statusIconKind', () => {
  it('marca el error con su propio icono', () => {
    expect(statusIconKind('failed')).toBe('failed');
  });

  it('el mensaje pendiente lleva reloj', () => {
    expect(statusIconKind('pending')).toBe('pending');
  });

  it('guardar en el vault no es entregar', () => {
    expect(statusIconKind('vaulted')).toBe('sent');
    expect(statusIconKind('sent')).toBe('sent');
  });

  it('solo entregado y leido llevan doble tick', () => {
    expect(statusIconKind('delivered')).toBe('delivered');
    expect(statusIconKind('read')).toBe('delivered');
  });

  it('un estado desconocido no presume de entrega', () => {
    expect(statusIconKind('raro' as ProtocolMessage['status'])).toBe('sent');
    expect(statusIconKind('' as ProtocolMessage['status'])).toBe('sent');
  });

  it('solo leido se resalta como leido', () => {
    expect(statusIconIsRead('read')).toBe(true);
    expect(statusIconIsRead('delivered')).toBe(false);
    expect(statusIconIsRead('vaulted')).toBe(false);
  });
});
