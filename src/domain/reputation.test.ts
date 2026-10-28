import { describe, expect, it } from 'vitest';

import { parseReputationScores, scoreOf } from './reputation';

const alice = 'a'.repeat(64);

describe('parseReputationScores', () => {
  it('reads a score map from the bridge', () => {
    expect(parseReputationScores(JSON.stringify({ [alice]: 42 }))).toEqual({ [alice]: 42 });
  });

  it('normalizes identifiers to lowercase', () => {
    expect(parseReputationScores(JSON.stringify({ [alice.toUpperCase()]: 42 }))).toEqual({ [alice]: 42 });
  });

  it('drops entries that are not finite numbers', () => {
    const raw = JSON.stringify({ [alice]: '42', bob: null, carol: 70 });
    expect(parseReputationScores(raw)).toEqual({ carol: 70 });
  });

  it('clamps and rounds scores into the scale', () => {
    expect(parseReputationScores(JSON.stringify({ a: 120, b: -5, c: 51.5 }))).toEqual({ a: 100, b: 0, c: 52 });
  });

  it('survives malformed payloads', () => {
    expect(parseReputationScores('')).toEqual({});
    expect(parseReputationScores('{')).toEqual({});
    expect(parseReputationScores('[1,2]')).toEqual({});
    expect(parseReputationScores(null)).toEqual({});
  });
});

describe('scoreOf', () => {
  const scores = { [alice]: 33 };

  it('returns the score of a contact', () => {
    expect(scoreOf(scores, alice)).toBe(33);
  });

  it('ignores case and padding', () => {
    expect(scoreOf(scores, ` ${alice.toUpperCase()} `)).toBe(33);
  });

  it('returns undefined when there is nothing to show', () => {
    expect(scoreOf(scores, undefined)).toBeUndefined();
    expect(scoreOf(scores, '')).toBeUndefined();
    expect(scoreOf(scores, 'b'.repeat(64))).toBeUndefined();
  });
});
