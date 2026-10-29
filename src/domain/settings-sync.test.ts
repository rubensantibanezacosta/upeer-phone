import { describe, expect, it } from 'vitest';

import {
  isEmptyConversationFlags,
  isRemoteSettingsBlock,
  mergeConversationFlags,
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
    expect(isRemoteSettingsBlock('privacy')).toBe(false);
    expect(isRemoteSettingsBlock(null)).toBe(false);
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
