import Storage from 'expo-sqlite/kv-store';

import { publishSettingsPatch, readSyncSettingsEvents } from '../../modules/upeer-transport/src/UPeerTransportModule';
import type { SyncSettingsEventSnapshot } from '../../modules/upeer-transport/src/UPeerTransport.types';
import { parseSettingsClocks, parseSettingsCursor, settingsEntityOf } from '@/domain/settings-sync';
import type { ChatList } from '@/domain/chat-lists';

const CLOCK_KEY = 'upeer.settingsClock';
const CURSOR_KEY = 'upeer.settingsCursor';
const PAGE = 50;

export type SettingsHandler = (values: Record<string, unknown>, occurredAt: number) => void;

export type SyncedProfileState = { alias: string; avatar: string };

let clocks: Record<string, number> = {};
let cursor = 0;
let loaded = false;
let loading: Promise<void> | null = null;
let draining = false;
let suppressProfilePublish = false;

async function persistClocks(): Promise<void> {
  await Storage.setItemAsync(CLOCK_KEY, JSON.stringify(clocks)).catch((cause: unknown) => {
    console.warn('No se pudieron guardar los relojes de ajustes.', cause);
  });
}

async function ensureLoaded(): Promise<void> {
  if (loaded) return;
  if (!loading) {
    loading = (async () => {
      clocks = parseSettingsClocks(await Storage.getItemAsync(CLOCK_KEY));
      cursor = parseSettingsCursor(await Storage.getItemAsync(CURSOR_KEY));
      loaded = true;
      loading = null;
    })();
  }
  await loading;
}

async function claim(entity: string, occurredAt: number): Promise<boolean> {
  if (!Number.isFinite(occurredAt) || occurredAt <= 0) return false;
  if (occurredAt <= (clocks[entity] ?? 0)) return false;
  clocks[entity] = occurredAt;
  await persistClocks();
  return true;
}

export async function publishChatFlags(chatId: string, flags: Record<string, boolean>): Promise<void> {
  await ensureLoaded();
  clocks[`chat:${chatId}`] = Date.now();
  await persistClocks();
  await publishSettingsPatch({ block: 'chat', chatId, ...flags }).catch((cause: unknown) => {
    console.warn('El servicio no pudo publicar los ajustes del chat.', cause);
  });
}

export async function publishMessageStarred(messageId: string, starred: boolean): Promise<void> {
  await ensureLoaded();
  clocks[`message:${messageId}`] = Date.now();
  await persistClocks();
  await publishSettingsPatch({ block: 'message', messageId, starred }).catch((cause: unknown) => {
    console.warn('El servicio no pudo publicar el destacado del mensaje.', cause);
  });
}

export async function publishChatList(list: ChatList): Promise<void> {
  await ensureLoaded();
  clocks[`lists:${list.id}`] = Date.now();
  await persistClocks();
  await publishSettingsPatch({ block: 'lists', listId: list.id, name: list.name, chatIds: list.chatIds }).catch((cause: unknown) => {
    console.warn('El servicio no pudo publicar la lista de chats.', cause);
  });
}

export async function publishChatListDeleted(listId: string): Promise<void> {
  await ensureLoaded();
  clocks[`lists:${listId}`] = Date.now();
  await persistClocks();
  await publishSettingsPatch({ block: 'lists', listId, deleted: true }).catch((cause: unknown) => {
    console.warn('El servicio no pudo publicar el borrado de la lista.', cause);
  });
}

export async function publishBlockedState(upeerId: string, blocked: boolean): Promise<void> {
  await ensureLoaded();
  clocks[`blocked:${upeerId}`] = Date.now();
  await persistClocks();
  await publishSettingsPatch({ block: 'blocked', upeerId, blocked }).catch((cause: unknown) => {
    console.warn('El servicio no pudo publicar el bloqueo del contacto.', cause);
  });
}

export async function publishProfileState(alias: string, avatar: string): Promise<void> {
  if (suppressProfilePublish) return;
  const state: SyncedProfileState = { alias: alias.trim(), avatar };
  await ensureLoaded();
  clocks.profile = Date.now();
  await persistClocks();
  await publishSettingsPatch({ block: 'profile', alias: state.alias, avatar: state.avatar }).catch((cause: unknown) => {
    console.warn('El servicio no pudo publicar el perfil de la cuenta.', cause);
  });
}

export async function withProfilePublishSuppressed<T>(task: () => Promise<T>): Promise<T> {
  suppressProfilePublish = true;
  try {
    return await task();
  } finally {
    suppressProfilePublish = false;
  }
}

export async function drainRemoteSettings(handlers: Record<string, SettingsHandler>): Promise<void> {
  if (draining) return;
  draining = true;
  try {
    await ensureLoaded();
    for (;;) {
      const page = await readSyncSettingsEvents(cursor, PAGE).catch((cause: unknown) => {
        console.warn('El servicio no devolvió los ajustes sincronizados.', cause);
        return null;
      });
      if (!page) break;
      if (page.cursor <= cursor && page.events.length === 0) break;
      await applyEvents(page.cursor, page.events, handlers);
      if (page.events.length < PAGE) break;
    }
  } finally {
    draining = false;
  }
}

async function applyEvents(
  nextCursor: number,
  events: SyncSettingsEventSnapshot[],
  handlers: Record<string, SettingsHandler>,
): Promise<void> {
  if (nextCursor > cursor) cursor = nextCursor;
  for (const event of events) {
    const payload = event.payload ?? {};
    const block = typeof payload.block === 'string' ? payload.block : '';
    const handler = handlers[block];
    if (!handler) continue;
    const entity = settingsEntityOf(payload);
    if (!entity) continue;
    if (!(await claim(entity, event.occurredAt))) continue;
    handler(payload, event.occurredAt);
  }
  await Storage.setItemAsync(CURSOR_KEY, String(cursor)).catch((cause: unknown) => {
    console.warn('No se pudo guardar el cursor de ajustes sincronizados.', cause);
  });
}

