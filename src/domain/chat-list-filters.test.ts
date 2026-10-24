import { describe, expect, it } from 'vitest';
import { filterChatEntries, isChatFilterActive, matchesChatQuery } from './chat-list-filters';

const entries = [
  { upeerId: 'a1b2c3d4e5f60718', title: 'Ana', preview: 'Hola, ¿qué tal?', unread: 2 },
  { upeerId: 'ff00ff00ff00ff00', title: 'Bruno', preview: '__SYS__|Contacto conectado', unread: 0 },
  { upeerId: '0102030405060708', title: 'Carla', preview: 'Nos vemos luego', unread: 1 },
  { upeerId: '1111222233334444', title: 'Diego', preview: 'Archivada', unread: 0, archived: true },
];

describe('chat list filters', () => {
  it('keeps every visible entry when the query is empty', () => {
    expect(filterChatEntries(entries, 'all', '')).toHaveLength(3);
  });

  it('hides archived conversations outside the archived filter', () => {
    expect(filterChatEntries(entries, 'all', '').map((entry) => entry.title)).toEqual(['Ana', 'Bruno', 'Carla']);
    expect(filterChatEntries(entries, 'unread', '').map((entry) => entry.title)).toEqual(['Ana', 'Carla']);
  });

  it('shows only archived conversations under the archived filter', () => {
    expect(filterChatEntries(entries, 'archived', '').map((entry) => entry.title)).toEqual(['Diego']);
  });

  it('ignores surrounding whitespace and case in the query', () => {
    expect(filterChatEntries(entries, 'all', '  aNA  ').map((entry) => entry.title)).toEqual(['Ana']);
  });

  it('searches identities, titles and previews', () => {
    expect(matchesChatQuery(entries[0], 'a1b2')).toBe(true);
    expect(matchesChatQuery(entries[1], 'bruno')).toBe(true);
    expect(matchesChatQuery(entries[2], 'luego')).toBe(true);
    expect(matchesChatQuery(entries[1], 'carla')).toBe(false);
  });

  it('keeps only unread conversations under the unread filter', () => {
    expect(filterChatEntries(entries, 'unread', '').map((entry) => entry.title)).toEqual(['Ana', 'Carla']);
  });

  it('combines the unread filter with the query', () => {
    expect(filterChatEntries(entries, 'unread', 'carla').map((entry) => entry.title)).toEqual(['Carla']);
    expect(filterChatEntries(entries, 'unread', 'bruno')).toHaveLength(0);
  });

  it('reports whether the list is being narrowed', () => {
    expect(isChatFilterActive('all', '   ')).toBe(false);
    expect(isChatFilterActive('unread', '')).toBe(true);
    expect(isChatFilterActive('archived', '')).toBe(true);
    expect(isChatFilterActive('all', 'ana')).toBe(true);
  });
});
