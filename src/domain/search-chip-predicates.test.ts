import { describe, expect, it } from 'vitest';

import { filterChatEntries, type ChatListFilter } from './chat-list-filters';
import { buildSearchSections, type SearchData } from './global-search';
import {
  isSearchableContact,
  matchesChatChip,
  matchesContactChip,
  matchesGroupChip,
  matchesMessageChip,
  type ChipChatLookup,
} from './search-chip-predicates';

const chips: ChatListFilter[] = ['all', 'unread', 'favorites', 'groups'];

const lookup = (entries: { upeerId: string; unread: number; favorite?: boolean; group?: boolean }[]): ChipChatLookup =>
  new Map(entries.map((entry) => [entry.upeerId, entry]));

describe('matchesChatChip', () => {
  it('resuelve las cuatro combinaciones de chip', () => {
    const read = { upeerId: 'r1', unread: 0 };
    const pending = { upeerId: 'p1', unread: 3 };
    const favorite = { upeerId: 'f1', unread: 0, favorite: true };
    const group = { upeerId: 'g1', unread: 0, group: true };
    expect(chips.map((chip) => matchesChatChip(read, chip))).toEqual([true, false, false, false]);
    expect(chips.map((chip) => matchesChatChip(pending, chip))).toEqual([true, true, false, false]);
    expect(chips.map((chip) => matchesChatChip(favorite, chip))).toEqual([true, false, true, false]);
    expect(chips.map((chip) => matchesChatChip(group, chip))).toEqual([true, false, false, true]);
  });

  it('mantiene un chat favorito y de grupo en ambos chips', () => {
    const entry = { upeerId: 'x1', unread: 1, favorite: true, group: true };
    expect(chips.every((chip) => matchesChatChip(entry, chip))).toBe(true);
  });
});

describe('matchesContactChip', () => {
  const chats = lookup([
    { upeerId: 'a1', unread: 2 },
    { upeerId: 'b2', unread: 0, favorite: true },
  ]);

  it('no filtra sin chat asociado salvo en no leídos y favoritos', () => {
    const contact = { upeerId: 'z9' };
    expect(chips.map((chip) => matchesContactChip(contact, chip, chats))).toEqual([true, false, false, true]);
  });

  it('usa el favorito del contacto', () => {
    const contact = { upeerId: 'z9', favorite: true };
    expect(chips.map((chip) => matchesContactChip(contact, chip, chats))).toEqual([true, false, true, true]);
  });

  it('usa los no leídos del chat asociado', () => {
    const contact = { upeerId: 'a1' };
    expect(chips.map((chip) => matchesContactChip(contact, chip, chats))).toEqual([true, true, false, true]);
  });

  it('descarta un contacto bloqueado en todos los chips', () => {
    const blocked = { upeerId: 'b2', favorite: true, blocked: true };
    expect(chips.map((chip) => matchesContactChip(blocked, chip, chats))).toEqual([false, false, false, false]);
    expect(isSearchableContact(blocked)).toBe(false);
    expect(isSearchableContact({ upeerId: 'b2' })).toBe(true);
  });
});

describe('matchesGroupChip', () => {
  it('hereda no leídos y favorito del chat del grupo', () => {
    const group = { groupId: 'g1' };
    expect(chips.map((chip) => matchesGroupChip(group, chip, lookup([{ upeerId: 'g1', unread: 1, favorite: true }])))).toEqual([true, true, true, true]);
    expect(chips.map((chip) => matchesGroupChip(group, chip, lookup([{ upeerId: 'g1', unread: 0 }])))).toEqual([true, false, false, true]);
    expect(chips.map((chip) => matchesGroupChip(group, chip, new Map()))).toEqual([true, false, false, true]);
  });
});

describe('matchesMessageChip', () => {
  const message = { chatUpeerId: 'c1' };

  it('deja pasar todo con el chip de todos', () => {
    expect(matchesMessageChip(message, 'all', new Map())).toBe(true);
  });

  it('hereda el chip del chat del mensaje', () => {
    const active = lookup([{ upeerId: 'c1', unread: 4, favorite: true }]);
    expect(chips.map((chip) => matchesMessageChip(message, chip, active))).toEqual([true, true, true, false]);
    const cold = lookup([{ upeerId: 'c1', unread: 0 }]);
    expect(chips.map((chip) => matchesMessageChip(message, chip, cold))).toEqual([true, false, false, false]);
  });

  it('descarta el mensaje cuando su chat no está en los datos', () => {
    expect(chips.map((chip) => matchesMessageChip(message, chip, new Map()))).toEqual([true, false, false, false]);
  });

  it('aplica el chip de grupos al chat de grupo', () => {
    expect(matchesMessageChip(message, 'groups', lookup([{ upeerId: 'c1', unread: 0, group: true }]))).toBe(true);
  });
});

describe('lista y secciones', () => {
  const entries: SearchData['chats'] = [
    { upeerId: 'a1', title: 'Chat Ana', preview: 'Sin mensajes', unread: 2, timestamp: 30 },
    { upeerId: 'b2', title: 'Chat Bruno', preview: 'Sin mensajes', unread: 0, timestamp: 20, favorite: true },
    { upeerId: 'g1', title: 'Chat Vecinos', preview: 'Sin mensajes', unread: 0, timestamp: 10, group: true },
  ];
  const expectedByChip: Record<ChatListFilter, string[]> = {
    all: ['a1', 'b2', 'g1'],
    unread: ['a1'],
    favorites: ['b2'],
    groups: ['g1'],
  };
  const chatIdsFromSections = (chip: ChatListFilter): string[] => {
    const data: SearchData = { chats: entries, contacts: [], groups: [], messages: [] };
    const section = buildSearchSections('chat', chip, data).find((candidate) => candidate.id === 'chats');
    return (section?.rows ?? []).map((row) => (row.target.kind === 'chat' ? row.target.upeerId : '')).sort();
  };

  it('coinciden para el mismo chat y el mismo chip', () => {
    for (const chip of chips) {
      const listed = filterChatEntries(entries, chip, '').map((entry) => entry.upeerId).sort();
      expect(listed).toEqual(expectedByChip[chip]);
      expect(chatIdsFromSections(chip)).toEqual(expectedByChip[chip]);
    }
  });

  it('excluye los contactos bloqueados de las secciones con cualquier chip', () => {
    const data: SearchData = {
      chats: [],
      contacts: [{ upeerId: 'z9', name: 'Zoe Bloqueada', address: '200::9', blocked: true, favorite: true }],
      groups: [],
      messages: [],
    };
    for (const chip of chips) {
      const section = buildSearchSections('zoe', chip, data).find((candidate) => candidate.id === 'contacts');
      expect(section?.rows ?? []).toEqual([]);
    }
  });
});
