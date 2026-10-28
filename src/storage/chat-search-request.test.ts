import { describe, expect, it } from 'vitest';

import { consumeChatSearch, getPendingChatSearch, requestChatSearch, subscribeChatSearch } from './chat-search-request';

const ALICE = 'a'.repeat(64);
const BOB = 'b'.repeat(64);

describe('chat-search-request', () => {
  it('no consume sin petición', () => {
    expect(getPendingChatSearch()).toBeNull();
    expect(consumeChatSearch(ALICE)).toBe(false);
  });

  it('consume la petición del chat que la pidió', () => {
    requestChatSearch(ALICE);

    expect(consumeChatSearch(BOB)).toBe(false);
    expect(consumeChatSearch(ALICE)).toBe(true);
    expect(consumeChatSearch(ALICE)).toBe(false);
    expect(getPendingChatSearch()).toBeNull();
  });

  it('normaliza el id a minúsculas', () => {
    requestChatSearch(ALICE.toUpperCase());

    expect(consumeChatSearch(ALICE)).toBe(true);
  });

  it('avisa a los suscriptores de cada petición nueva', () => {
    let calls = 0;
    const unsubscribe = subscribeChatSearch(() => { calls += 1; });

    requestChatSearch(ALICE);
    consumeChatSearch(ALICE);
    unsubscribe();
    requestChatSearch(BOB);

    expect(calls).toBe(1);
    expect(consumeChatSearch(BOB)).toBe(true);
  });
});
