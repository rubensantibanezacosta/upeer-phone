import { describe, expect, it } from 'vitest';

import { isReactedByMe, summarizeReactions } from './chat-reactions';

const ME = 'a'.repeat(64);
const PEER = 'b'.repeat(64);

describe('summarizeReactions', () => {
  it('returns an empty list without reactions', () => {
    expect(summarizeReactions(undefined, ME)).toEqual([]);
  });

  it('counts actors and flags the own reaction', () => {
    const summary = summarizeReactions({ '👍': [ME, PEER], '❤️': [PEER] }, ME);
    expect(summary).toEqual([
      { emoji: '👍', count: 2, mine: true },
      { emoji: '❤️', count: 1, mine: false },
    ]);
  });

  it('ignores empty actor lists', () => {
    expect(summarizeReactions({ '👍': [] }, ME)).toEqual([]);
  });

  it('matches the own identity case-insensitively', () => {
    const summary = summarizeReactions({ '👍': [ME.toUpperCase()] }, ME);
    expect(summary[0].mine).toBe(true);
  });
});

describe('isReactedByMe', () => {
  it('is false when the emoji is absent', () => {
    expect(isReactedByMe({}, '👍', ME)).toBe(false);
    expect(isReactedByMe(undefined, '👍', ME)).toBe(false);
  });

  it('is true when the own identity reacted', () => {
    expect(isReactedByMe({ '👍': [PEER, ME] }, '👍', ME)).toBe(true);
  });

  it('is false for reactions of other peers', () => {
    expect(isReactedByMe({ '👍': [PEER] }, '👍', ME)).toBe(false);
  });
});
