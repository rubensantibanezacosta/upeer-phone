import Storage from 'expo-sqlite/kv-store';

import { parseStarred, setStarred, toggleStarred, type StarredMap } from '@/domain/starred';
import { mergeStarredMessage } from '@/domain/settings-sync';
import { publishMessageStarred } from '@/storage/settings-sync-client';

const STORAGE_KEY = 'upeer.starredMessages';

let current: StarredMap = {};
let loading: Promise<void> | null = null;
const listeners = new Set<() => void>();

function emit() {
  for (const listener of listeners) listener();
}

async function persist(next: StarredMap): Promise<void> {
  current = next;
  emit();
  await Storage.setItemAsync(STORAGE_KEY, JSON.stringify(next)).catch((cause: unknown) => {
    console.warn('No se pudieron guardar los mensajes destacados.', cause);
  });
}

export function subscribeStarred(listener: () => void): () => void {
  listeners.add(listener);
  return () => { listeners.delete(listener); };
}

export function getStarred(): StarredMap {
  return current;
}

export async function loadStarred(): Promise<void> {
  if (loading) return loading;
  loading = (async () => {
    try {
      current = parseStarred(await Storage.getItemAsync(STORAGE_KEY));
      emit();
    } catch (cause: unknown) {
      console.warn('No se pudieron cargar los mensajes destacados.', cause);
    }
  })();
  return loading;
}

export async function toggleStarredMessage(id: string): Promise<void> {
  await persist(toggleStarred(current, id));
  await publishMessageStarred(id, Boolean(current[id]));
}

export async function setStarredMessages(ids: readonly string[], value: boolean): Promise<void> {
  await persist(setStarred(current, ids, value));
  for (const id of ids) {
    if (!id) continue;
    await publishMessageStarred(id, value);
  }
}

export function applyRemoteStarred(values: Record<string, unknown>): void {
  const next = mergeStarredMessage(current, values);
  if (!next) return;
  current = next;
  emit();
  Storage.setItemAsync(STORAGE_KEY, JSON.stringify(current)).catch((cause: unknown) => {
    console.warn('No se pudieron guardar los mensajes destacados sincronizados.', cause);
  });
}
