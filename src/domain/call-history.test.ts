import { describe, expect, it } from 'vitest';

import {
  collectRecentCalls,
  parseRecentCall,
  recentCallKindLabel,
  recentCallMissed,
  recentCallResultLabel,
} from './call-history';
import type { ProtocolMessage } from '../../modules/upeer-transport/src/UPeerTransport.types';

function message(overrides: Partial<ProtocolMessage>): ProtocolMessage {
  return {
    id: 'm1',
    upeerId: 'peer-a',
    isMine: false,
    content: '__SYS__|Llamada de voz perdida',
    status: 'delivered',
    timestamp: 1_730_000_000_000,
    ...overrides,
  };
}

describe('parseRecentCall', () => {
  it('lee una llamada perdida entrante', () => {
    expect(parseRecentCall(message({}), 'peer-a')).toEqual({
      id: 'm1',
      chatId: 'peer-a',
      timestamp: 1_730_000_000_000,
      kind: 'audio',
      duration: null,
      outcome: 'missed',
      isMine: false,
    });
  });

  it('prefiere el inicio de la llamada sobre la marca del mensaje', () => {
    const call = parseRecentCall(message({ content: '__SYS__|Videollamada · 12:05 · 1730000009999', timestamp: 1_730_001_111_111 }), 'peer-b');
    expect(call?.timestamp).toBe(1_730_000_009_999);
    expect(call?.duration).toBe('12:05');
    expect(call?.kind).toBe('video');
  });

  it('ignora los mensajes que no son de llamada', () => {
    expect(parseRecentCall(message({ content: 'hola' }), 'peer-a')).toBeNull();
    expect(parseRecentCall(message({ content: '__SYS__|Ha salido del grupo' }), 'peer-a')).toBeNull();
  });
});

describe('collectRecentCalls', () => {
  it('junta las llamadas de todas las conversaciones y las ordena de reciente a antigua', () => {
    const calls = collectRecentCalls([
      { chatId: 'peer-a', messages: [message({ id: 'a1', timestamp: 1_000 }), message({ id: 'a2', timestamp: 3_000, content: '__SYS__|Videollamada perdida' })] },
      { chatId: 'peer-b', messages: [message({ id: 'b1', timestamp: 2_000, content: 'texto' }), message({ id: 'b2', timestamp: 4_000, content: '__SYS__|Llamada de voz · 1:03' })] },
    ]);
    expect(calls.map((call) => call.id)).toEqual(['b2', 'a2', 'a1']);
    expect(calls.map((call) => call.chatId)).toEqual(['peer-b', 'peer-a', 'peer-a']);
  });

  it('no repite la misma llamada y respeta el limite', () => {
    const repeated = message({ id: 'same' });
    const calls = collectRecentCalls([
      { chatId: 'peer-a', messages: [repeated, repeated] },
      { chatId: 'peer-b', messages: [message({ id: 'other', timestamp: 1_740_000_000_000 })] },
    ], 1);
    expect(calls).toHaveLength(1);
    expect(calls[0].id).toBe('other');
  });
});

describe('etiquetas de la llamada', () => {
  it('describe el tipo y el resultado', () => {
    expect(recentCallKindLabel({ kind: 'video' } as never)).toBe('Videollamada');
    expect(recentCallKindLabel({ kind: 'audio' } as never)).toBe('Llamada de voz');
    expect(recentCallResultLabel({ duration: '2:31', outcome: null } as never)).toBe('2:31');
    expect(recentCallResultLabel({ duration: null, outcome: 'rejected' } as never)).toBe('Rechazada');
    expect(recentCallResultLabel({ duration: null, outcome: null } as never)).toBe('Perdida');
  });

  it('marca como perdida solo la llamada entrante que no se contesto', () => {
    expect(recentCallMissed({ duration: null, outcome: null, isMine: false } as never)).toBe(true);
    expect(recentCallMissed({ duration: null, outcome: null, isMine: true } as never)).toBe(false);
    expect(recentCallMissed({ duration: null, outcome: 'rejected', isMine: false } as never)).toBe(false);
    expect(recentCallMissed({ duration: '1:03', outcome: null, isMine: false } as never)).toBe(false);
  });
});
