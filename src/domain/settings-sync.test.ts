import { describe, expect, it } from 'vitest';

import {
  applyRemoteChatList,
  isEmptyConversationFlags,
  isRemoteSettingsBlock,
  mergeConversationFlags,
  mergeStarredMessage,
  parseSettingsClocks,
  parseSettingsCursor,
  settingsEntityOf,
} from '@/domain/settings-sync';

describe('settings-sync del móvil', () => {
  it('la entidad de un ajuste de chat lleva el chat', () => {
    expect(settingsEntityOf({ block: 'chat', chatId: 'grp-1' })).toBe('chat:grp-1');
    expect(settingsEntityOf({ block: 'chat', chatId: '  grp-1  ' })).toBe('chat:grp-1');
  });

  it('rechaza bloques desconocidos y chats vacíos', () => {
    expect(settingsEntityOf({ block: 'retention', chatId: 'grp-1' })).toBeNull();
    expect(settingsEntityOf({ block: 'chat' })).toBeNull();
    expect(settingsEntityOf({ block: 'chat', chatId: '   ' })).toBeNull();
    expect(settingsEntityOf({ block: 42, chatId: 'grp-1' })).toBeNull();
  });

  it('solo el bloque de chat viaja como ajuste remoto', () => {
    expect(isRemoteSettingsBlock('chat')).toBe(true);
    expect(isRemoteSettingsBlock('message')).toBe(true);
    expect(isRemoteSettingsBlock('privacy')).toBe(false);
    expect(isRemoteSettingsBlock(null)).toBe(false);
  });

  it('la entidad de un mensaje destacado es el mensaje', () => {
    expect(settingsEntityOf({ block: 'message', messageId: ' msg-1 ' })).toBe('message:msg-1');
    expect(settingsEntityOf({ block: 'message' })).toBeNull();
  });

  it('mezcla el destacado de un mensaje', () => {
    expect(mergeStarredMessage({}, { messageId: 'm1', starred: true })).toEqual({ m1: true });
    expect(mergeStarredMessage({ m1: true }, { messageId: 'm1', starred: false })).toEqual({});
  });

  it('un destacado que no cambia nada no devuelve nada', () => {
    expect(mergeStarredMessage({ m1: true }, { messageId: 'm1', starred: true })).toBeNull();
    expect(mergeStarredMessage({}, { messageId: 'm1' })).toBeNull();
    expect(mergeStarredMessage({}, { starred: true })).toBeNull();
  });

  it('la entidad de una lista es la lista', () => {
    expect(settingsEntityOf({ block: 'lists', listId: ' l1 ' })).toBe('lists:l1');
    expect(settingsEntityOf({ block: 'lists' })).toBeNull();
  });

  it('la entidad de un bloqueo es el contacto', () => {
    expect(settingsEntityOf({ block: 'blocked', upeerId: ' peer-1 ' })).toBe('blocked:peer-1');
    expect(settingsEntityOf({ block: 'blocked' })).toBeNull();
    expect(isRemoteSettingsBlock('blocked')).toBe(true);
    expect(settingsEntityOf({ block: 'profile', alias: 'Rubén' })).toBe('profile');
    expect(settingsEntityOf({ block: 'profile' })).toBe('profile');
    expect(isRemoteSettingsBlock('profile')).toBe(true);
  });

  it('crea una lista que no existia', () => {
    expect(applyRemoteChatList([], { listId: 'l1', name: 'Trabajo', chatIds: ['a', 'b'] }))
      .toEqual([{ id: 'l1', name: 'Trabajo', chatIds: ['a', 'b'] }]);
  });

  it('renombra y reordena sin duplicar', () => {
    const lists = [{ id: 'l1', name: 'Trabajo', chatIds: ['a'] }];
    expect(applyRemoteChatList(lists, { listId: 'l1', name: 'Oficina', chatIds: ['b', 'a'] }))
      .toEqual([{ id: 'l1', name: 'Oficina', chatIds: ['b', 'a'] }]);
  });

  it('borra una lista existente y no inventa borrados', () => {
    const lists = [{ id: 'l1', name: 'Trabajo', chatIds: [] }];
    expect(applyRemoteChatList(lists, { listId: 'l1', deleted: true })).toEqual([]);
    expect(applyRemoteChatList(lists, { listId: 'l2', deleted: true })).toBeNull();
  });

  it('una lista sin nombre o sin cambios no devuelve nada', () => {
    const lists = [{ id: 'l1', name: 'Trabajo', chatIds: ['a'] }];
    expect(applyRemoteChatList(lists, { listId: 'l1', name: '   ' })).toBeNull();
    expect(applyRemoteChatList(lists, { listId: 'l1', name: 'Trabajo', chatIds: ['a'] })).toBeNull();
    expect(applyRemoteChatList(lists, { name: 'Trabajo' })).toBeNull();
  });

  it('lee los relojes guardados y descarta lo imposible', () => {
    expect(parseSettingsClocks(null)).toEqual({});
    expect(parseSettingsClocks('no es json')).toEqual({});
    expect(parseSettingsClocks(JSON.stringify({ 'chat:a': 10, 'chat:b': -1, 'chat:c': 'x' }))).toEqual({ 'chat:a': 10 });
  });

  it('el cursor empieza en cero si no hay nada válido', () => {
    expect(parseSettingsCursor(null)).toBe(0);
    expect(parseSettingsCursor('-5')).toBe(0);
    expect(parseSettingsCursor('doce')).toBe(0);
    expect(parseSettingsCursor('42')).toBe(42);
  });

  it('mezcla solo las banderas presentes', () => {
    expect(mergeConversationFlags({ archived: false, muted: false, pinned: true }, { muted: true }))
      .toEqual({ archived: false, muted: true, pinned: true });
  });

  it('un valor que no cambia nada no devuelve nada', () => {
    expect(mergeConversationFlags({ archived: false, muted: true, pinned: false }, { muted: true })).toBeNull();
    expect(mergeConversationFlags({ archived: false, muted: false, pinned: false }, { unread: true })).toBeNull();
  });

  it('detecta cuando ya no queda ninguna bandera', () => {
    expect(isEmptyConversationFlags({ archived: false, muted: false, pinned: false })).toBe(true);
    expect(isEmptyConversationFlags({ archived: true, muted: false, pinned: false })).toBe(false);
  });
});
