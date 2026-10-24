import { describe, expect, it } from 'vitest';
import {
  EMPTY_CONVERSATION_FLAGS,
  flagsFor,
  mutedPeerIds,
  orderByPinned,
  parseConversationFlags,
  toggleFlag,
} from './conversation-flags';

const ana = 'aa'.repeat(32);
const bruno = 'bb'.repeat(32);

describe('conversation flags', () => {
  it('starts with every flag off', () => {
    expect(flagsFor({}, ana)).toEqual(EMPTY_CONVERSATION_FLAGS);
  });

  it('toggles a single flag without touching the others', () => {
    const muted = toggleFlag({}, ana, 'muted');
    expect(flagsFor(muted, ana)).toEqual({ archived: false, muted: true, pinned: false });
    expect(flagsFor(muted, bruno)).toEqual(EMPTY_CONVERSATION_FLAGS);
  });

  it('drops the entry once every flag is off again', () => {
    const muted = toggleFlag({}, ana, 'muted');
    expect(toggleFlag(muted, ana, 'muted')).toEqual({});
  });

  it('keeps flags per conversation', () => {
    const map = toggleFlag(toggleFlag({}, ana, 'pinned'), bruno, 'archived');
    expect(flagsFor(map, ana).pinned).toBe(true);
    expect(flagsFor(map, bruno).archived).toBe(true);
    expect(flagsFor(map, bruno).pinned).toBe(false);
  });

  it('reads stored json and ignores unknown identities', () => {
    const map = parseConversationFlags(JSON.stringify({ [ana]: { muted: true, pinned: 'yes' }, '': { muted: true }}));
    expect(map).toEqual({ [ana]: { archived: false, muted: true, pinned: false } });
  });

  it('falls back to an empty map on garbage', () => {
    expect(parseConversationFlags('no-json')).toEqual({});
    expect(parseConversationFlags(null)).toEqual({});
  });

  it('lists the muted identities for the service', () => {
    const map = toggleFlag(toggleFlag({}, ana, 'muted'), bruno, 'muted');
    expect(mutedPeerIds(map).sort()).toEqual([ana, bruno].sort());
  });

  it('moves pinned conversations to the front keeping the rest in order', () => {
    const entries = [{ upeerId: ana }, { upeerId: bruno }, { upeerId: 'cc'.repeat(32) }];
    const map = toggleFlag({}, 'cc'.repeat(32), 'pinned');
    expect(orderByPinned(entries, map).map((entry) => entry.upeerId)).toEqual(['cc'.repeat(32), ana, bruno]);
    expect(orderByPinned(entries, {})).toEqual(entries);
  });
});
