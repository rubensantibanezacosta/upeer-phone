import { describe, expect, it } from 'vitest';

import type { StarredMap } from './starred';
import { allStarred, collectStarred, countStarred, isStarred, parseStarred, sanitizeStarred, setStarred, toggleStarred } from './starred';

type Message = { id: string; isMine: boolean; timestamp: number; deleted?: boolean };

const message = (id: string, timestamp: number, extra: Partial<Message> = {}): Message => ({ id, isMine: true, timestamp, ...extra });

describe('toggleStarred', () => {
  it('marks a message', () => {
    expect(toggleStarred({}, 'm1')).toEqual({ m1: true });
  });

  it('unmarks a starred message', () => {
    expect(toggleStarred({ m1: true }, 'm1')).toEqual({});
  });

  it('does not mutate the original map', () => {
    const original: StarredMap = { m1: true };

    toggleStarred(original, 'm1');

    expect(original).toEqual({ m1: true });
  });

  it('ignores empty ids', () => {
    expect(toggleStarred({}, '')).toEqual({});
  });
});

describe('isStarred', () => {
  it('reads the mark', () => {
    expect(isStarred({ m1: true }, 'm1')).toBe(true);
    expect(isStarred({ m1: true }, 'm2')).toBe(false);
    expect(isStarred({}, null)).toBe(false);
  });
});

describe('setStarred', () => {
  it('marks every id', () => {
    expect(setStarred({}, ['a', 'b'], true)).toEqual({ a: true, b: true });
  });

  it('removes every id', () => {
    expect(setStarred({ a: true, b: true, c: true }, ['a', 'b'], false)).toEqual({ c: true });
  });

  it('skips empty ids', () => {
    expect(setStarred({}, ['', 'a'], true)).toEqual({ a: true });
  });
});

describe('allStarred', () => {
  it('is true only when every id is marked', () => {
    expect(allStarred({ a: true, b: true }, ['a', 'b'])).toBe(true);
    expect(allStarred({ a: true }, ['a', 'b'])).toBe(false);
  });

  it('is false without ids', () => {
    expect(allStarred({}, [])).toBe(false);
    expect(allStarred({}, [''])).toBe(false);
  });
});

describe('sanitizeStarred', () => {
  it('keeps only true marks with a key', () => {
    expect(sanitizeStarred({ a: true, b: false, '': true, c: 'yes' })).toEqual({ a: true });
  });

  it('rejects non objects', () => {
    expect(sanitizeStarred(null)).toEqual({});
    expect(sanitizeStarred(['a'])).toEqual({});
  });
});

describe('parseStarred', () => {
  it('reads stored JSON', () => {
    expect(parseStarred(JSON.stringify({ a: true }))).toEqual({ a: true });
  });

  it('survives broken payloads', () => {
    expect(parseStarred('{')).toEqual({});
    expect(parseStarred(null)).toEqual({});
  });
});

describe('collectStarred', () => {
  it('returns the starred messages newest first', () => {
    const messages = [message('a', 1), message('b', 2), message('c', 3)];

    expect(collectStarred(messages, { a: true, c: true }).map((entry) => entry.id)).toEqual(['c', 'a']);
  });

  it('skips deleted messages', () => {
    const messages = [message('a', 1, { deleted: true }), message('b', 2)];

    expect(collectStarred(messages, { a: true, b: true }).map((entry) => entry.id)).toEqual(['b']);
  });

  it('breaks ties by position', () => {
    const messages = [message('a', 5), message('b', 5)];

    expect(collectStarred(messages, { a: true, b: true }).map((entry) => entry.id)).toEqual(['a', 'b']);
  });

  it('ignores unmarked messages', () => {
    expect(collectStarred([message('a', 1)], {})).toEqual([]);
  });
});

describe('countStarred', () => {
  it('counts the marks of the loaded history', () => {
    expect(countStarred([message('a', 1), message('b', 2)], { a: true, b: true })).toBe(2);
    expect(countStarred([message('a', 1)], { z: true })).toBe(0);
  });
});
