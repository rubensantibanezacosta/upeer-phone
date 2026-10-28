import { describe, expect, it, vi } from 'vitest';

import {
  createChatList,
  deleteChatList,
  describeChatList,
  findChatList,
  LIST_NAME_MAX_LENGTH,
  MAX_CHAT_LISTS,
  listsForChat,
  nextListId,
  parseChatLists,
  renameChatList,
  restrictToChatList,
  sanitizeChatIds,
  sanitizeChatLists,
  sanitizeListName,
  toggleChatInList,
  type ChatList,
} from './chat-lists';

const list = (id: string, name: string, chatIds: string[] = []): ChatList => ({ id, name, chatIds });

describe('sanitizeListName', () => {
  it('recorta, colapsa espacios y limita la longitud', () => {
    expect(sanitizeListName('  Trabajo   y   más  ')).toBe('Trabajo y más');
    expect(sanitizeListName('a'.repeat(LIST_NAME_MAX_LENGTH + 10))).toHaveLength(LIST_NAME_MAX_LENGTH);
  });

  it('descarta lo que no es texto', () => {
    expect(sanitizeListName(undefined)).toBe('');
    expect(sanitizeListName(42)).toBe('');
  });
});

describe('sanitizeChatIds', () => {
  it('quita repetidos, vacíos y lo que no es texto', () => {
    expect(sanitizeChatIds(['a', ' a ', '', 7, null, 'b'])).toEqual(['a', 'b']);
  });

  it('descarta lo que no es lista', () => {
    expect(sanitizeChatIds(null)).toEqual([]);
  });
});

describe('sanitizeChatLists', () => {
  it('conserva solo listas con nombre e id únicos', () => {
    const result = sanitizeChatLists([
      { id: 'list-1', name: 'Trabajo', chatIds: ['a'] },
      { id: 'list-1', name: 'Duplicada', chatIds: [] },
      { id: 'list-2', name: '   ', chatIds: [] },
      { id: '', name: 'Sin id', chatIds: [] },
      'basura',
    ]);
    expect(result).toEqual([list('list-1', 'Trabajo', ['a'])]);
  });

  it('corta al máximo de listas', () => {
    const many = Array.from({ length: MAX_CHAT_LISTS + 5 }, (_, index) => ({ id: `list-${index}`, name: `L${index}`, chatIds: [] }));
    expect(sanitizeChatLists(many)).toHaveLength(MAX_CHAT_LISTS);
  });
});

describe('parseChatLists', () => {
  it('lee el JSON guardado', () => {
    expect(parseChatLists(JSON.stringify([{ id: 'list-1', name: 'Familia', chatIds: ['a', 'b'] }]))).toEqual([list('list-1', 'Familia', ['a', 'b'])]);
  });

  it('devuelve lista vacía sin dato', () => {
    expect(parseChatLists(null)).toEqual([]);
    expect(parseChatLists('')).toEqual([]);
  });

  it('avisa y empieza sin listas si el JSON está roto', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    expect(parseChatLists('{no es json')).toEqual([]);
    expect(warn).toHaveBeenCalled();
    warn.mockRestore();
  });
});

describe('createChatList', () => {
  it('crea la lista con el primer id libre', () => {
    const { lists, list: created } = createChatList([list('list-1', 'Uno')], 'Trabajo');
    expect(created).toEqual(list('list-2', 'Trabajo'));
    expect(lists).toHaveLength(2);
  });

  it('no crea nada sin nombre', () => {
    const result = createChatList([], '   ');
    expect(result.list).toBeNull();
    expect(result.lists).toEqual([]);
  });

  it('no pasa del máximo de listas', () => {
    const full = Array.from({ length: MAX_CHAT_LISTS }, (_, index) => list(`list-${index}`, `L${index}`));
    expect(createChatList(full, 'Otra').list).toBeNull();
  });
});

describe('nextListId', () => {
  it('salta los ids ocupados', () => {
    expect(nextListId([list('list-2', 'Dos')])).toBe('list-1');
    expect(nextListId([list('list-1', 'Uno'), list('list-2', 'Dos')])).toBe('list-3');
  });
});

describe('renameChatList', () => {
  it('cambia el nombre de la lista elegida', () => {
    const result = renameChatList([list('list-1', 'Uno'), list('list-2', 'Dos')], 'list-2', 'Trabajo');
    expect(result.map((entry) => entry.name)).toEqual(['Uno', 'Trabajo']);
  });

  it('ignora un nombre vacío', () => {
    const lists = [list('list-1', 'Uno')];
    expect(renameChatList(lists, 'list-1', '  ')).toBe(lists);
  });
});

describe('deleteChatList', () => {
  it('quita la lista elegida', () => {
    expect(deleteChatList([list('list-1', 'Uno'), list('list-2', 'Dos')], 'list-1')).toEqual([list('list-2', 'Dos')]);
  });
});

describe('toggleChatInList', () => {
  it('añade y quita el chat', () => {
    const added = toggleChatInList([list('list-1', 'Uno')], 'list-1', 'a');
    expect(added[0].chatIds).toEqual(['a']);
    expect(toggleChatInList(added, 'list-1', 'a')[0].chatIds).toEqual([]);
  });

  it('no toca las demás listas', () => {
    const lists = [list('list-1', 'Uno'), list('list-2', 'Dos')];
    expect(toggleChatInList(lists, 'list-1', 'a')[1]).toBe(lists[1]);
  });

  it('ignora un chat vacío', () => {
    const lists = [list('list-1', 'Uno')];
    expect(toggleChatInList(lists, 'list-1', '')).toBe(lists);
  });
});

describe('findChatList y listsForChat', () => {
  const lists = [list('list-1', 'Uno', ['a']), list('list-2', 'Dos', ['a', 'b'])];

  it('encuentra por id y devuelve null sin id', () => {
    expect(findChatList(lists, 'list-2')?.name).toBe('Dos');
    expect(findChatList(lists, null)).toBeNull();
    expect(findChatList(lists, 'list-9')).toBeNull();
  });

  it('dice en qué listas está un chat', () => {
    expect(listsForChat(lists, 'a').map((entry) => entry.id)).toEqual(['list-1', 'list-2']);
    expect(listsForChat(lists, 'z')).toEqual([]);
  });
});

describe('restrictToChatList', () => {
  const entries = [{ upeerId: 'a' }, { upeerId: 'b' }, { upeerId: 'c' }];

  it('sin lista no filtra', () => {
    expect(restrictToChatList(entries, null)).toBe(entries);
  });

  it('deja solo los chats de la lista', () => {
    expect(restrictToChatList(entries, list('list-1', 'Uno', ['a', 'c']))).toEqual([{ upeerId: 'a' }, { upeerId: 'c' }]);
  });
});

describe('describeChatList', () => {
  it('concuerda en singular y plural', () => {
    expect(describeChatList(list('list-1', 'Uno', ['a']))).toBe('1 chat');
    expect(describeChatList(list('list-1', 'Uno'))).toBe('0 chats');
    expect(describeChatList(list('list-1', 'Uno', ['a', 'b']))).toBe('2 chats');
  });
});
