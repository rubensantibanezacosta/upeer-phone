import { describe, expect, it } from 'vitest';

import { TYPING_INTERVAL_MS, decideTypingSignal } from './typing-activity';

const NOW = 1_000_000;

describe('decideTypingSignal', () => {
  it('avisa de escritura la primera vez', () => {
    expect(decideTypingSignal({ hasText: true, lastSentAt: 0, now: NOW })).toBe('typing');
  });

  it('no repite el aviso dentro de la ventana', () => {
    expect(decideTypingSignal({ hasText: true, lastSentAt: NOW - TYPING_INTERVAL_MS + 1, now: NOW })).toBeNull();
  });

  it('vuelve a avisar pasada la ventana', () => {
    expect(decideTypingSignal({ hasText: true, lastSentAt: NOW - TYPING_INTERVAL_MS, now: NOW })).toBe('typing');
  });

  it('para al vaciar el campo solo si había aviso', () => {
    expect(decideTypingSignal({ hasText: false, lastSentAt: NOW - 10, now: NOW })).toBe('stop');
    expect(decideTypingSignal({ hasText: false, lastSentAt: 0, now: NOW })).toBeNull();
  });
});
