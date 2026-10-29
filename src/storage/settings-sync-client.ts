import Storage from 'expo-sqlite/kv-store';

import { publishSettingsPatch, readSyncEventsAfter } from '../../modules/upeer-transport/src/UPeerTransportModule';
import type { SyncEventRecordSnapshot } from '../../modules/upeer-transport/src/UPeerTransport.types';
import { parseSettingsClocks, parseSettingsCursor, settingsEntityOf } from '@/domain/settings-sync';

const CLOCK_KEY = 'upeer.settingsClock';
const CURSOR_KEY = 'upeer.settingsCursor';
const PAGE = 50;

export type SettingsHandler = (values: Record<string, unknown>, occurredAt: number) => void;

let clocks: Record<string, number> = {};
let cursor = 0;
let loaded = false;
let loading: Promise<void> | null = null;
let draining = false;

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

export async function drainRemoteSettings(handlers: Record<string, SettingsHandler>): Promise<void> {
  if (draining) return;
  draining = true;
  try {
    await ensureLoaded();
    for (;;) {
      const events = await readSyncEventsAfter(cursor, PAGE).catch((cause: unknown) => {
        console.warn('El servicio no devolvió los ajustes sincronizados.', cause);
        return [] as SyncEventRecordSnapshot[];
      });
      if (events.length === 0) break;
      await applyEvents(events, handlers);
      if (events.length < PAGE) break;
    }
  } finally {
    draining = false;
  }
}

async function applyEvents(events: SyncEventRecordSnapshot[], handlers: Record<string, SettingsHandler>): Promise<void> {
  for (const event of events) {
    if (event.sequence > cursor) cursor = event.sequence;
    if (event.kind !== 'SETTINGS_UPDATE') continue;
    if (event.direction !== 'inbound') continue;
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

