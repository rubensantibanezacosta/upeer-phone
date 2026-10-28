import { describe, expect, it } from 'vitest';

import { describeStarredTotal, groupStarredByChat, shortenedChatName, starredTotal, toStarredGlobalRows, type StarrableGlobalMessage } from './starred-global';

const chatA = 'aa'.repeat(32);
const chatB = 'bb'.repeat(32);

const message = (id: string, chatId: string, timestamp: number, extra: Partial<StarrableGlobalMessage> = {}): StarrableGlobalMessage => ({
  id,
  upeerId: chatId,
  content: `texto ${id}`,
  timestamp,
  isMine: true,
  ...extra,
});

describe('shortenedChatName', () => {
  it('keeps short ids', () => {
    expect(shortenedChatName('abc')).toBe('abc');
  });

  it('trims long ids', () => {
    const name = shortenedChatName(chatA);

    expect(name).toHaveLength(33);
    expect(name.endsWith('…')).toBe(true);
  });
});

describe('toStarredGlobalRows', () => {
  it('keeps only the starred messages, newest first', () => {
    const messages = [message('a', chatA, 1), message('b', chatB, 3), message('c', chatA, 2)];

    expect(toStarredGlobalRows(messages, { b: true, c: true }).map((row) => row.id)).toEqual(['b', 'c']);
  });

  it('drops deleted messages', () => {
    const messages = [message('a', chatA, 1, { deleted: true }), message('b', chatA, 2)];

    expect(toStarredGlobalRows(messages, { a: true, b: true }).map((row) => row.id)).toEqual(['b']);
  });

  it('drops messages without chat', () => {
    const messages = [message('a', '', 1)];

    expect(toStarredGlobalRows(messages, { a: true })).toEqual([]);
  });

  it('normalises broken timestamps', () => {
    const rows = toStarredGlobalRows([message('a', chatA, Number.NaN)], { a: true });

    expect(rows[0]?.timestamp).toBe(0);
  });
});

describe('groupStarredByChat', () => {
  it('groups by chat and orders by the newest message', () => {
    const rows = toStarredGlobalRows(
      [message('a1', chatA, 1), message('a2', chatA, 5), message('b1', chatB, 9)],
      { a1: true, a2: true, b1: true },
    );

    const groups = groupStarredByChat(rows, { [chatA]: 'Ana', [chatB]: 'Grupo' });

    expect(groups.map((group) => group.name)).toEqual(['Grupo', 'Ana']);
    expect(groups[0]?.rows.map((row) => row.id)).toEqual(['b1']);
    expect(groups[1]?.rows.map((row) => row.id)).toEqual(['a2', 'a1']);
  });

  it('falls back to the shortened id when there is no name', () => {
    const rows = toStarredGlobalRows([message('a1', chatA, 1)], { a1: true });

    expect(groupStarredByChat(rows, {})[0]?.name).toBe(shortenedChatName(chatA));
  });

  it('ignores blank names', () => {
    const rows = toStarredGlobalRows([message('a1', chatA, 1)], { a1: true });

    expect(groupStarredByChat(rows, { [chatA]: '   ' })[0]?.name).toBe(shortenedChatName(chatA));
  });

  it('returns nothing without rows', () => {
    expect(groupStarredByChat([], {})).toEqual([]);
  });
});

describe('starredTotal y describeStarredTotal', () => {
  it('counts every row', () => {
    const rows = toStarredGlobalRows([message('a1', chatA, 1), message('b1', chatB, 2)], { a1: true, b1: true });

    expect(starredTotal(groupStarredByChat(rows, {}))).toBe(2);
  });

  it('describes the count', () => {
    expect(describeStarredTotal(0)).toBe('Ningún mensaje destacado');
    expect(describeStarredTotal(1)).toBe('1 mensaje destacado');
    expect(describeStarredTotal(4)).toBe('4 mensajes destacados');
  });
});
