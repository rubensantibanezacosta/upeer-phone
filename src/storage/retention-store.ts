import Storage from 'expo-sqlite/kv-store';

import { parseRetentionMap, setRetention, type RetentionId, type RetentionMap } from '@/domain/retention';
import { setRetentionMap } from '../../modules/upeer-transport/src/UPeerTransportModule';

const STORAGE_KEY = 'upeer.chatRetention';

let current: RetentionMap = {};
let loading: Promise<void> | null = null;
const listeners = new Set<() => void>();

function emit() {
  for (const listener of listeners) listener();
}

async function pushRetentionMap(): Promise<void> {
  await setRetentionMap(JSON.stringify(current)).catch((cause: unknown) => {
    console.warn('El servicio no recibió los mensajes temporales.', cause);
  });
}

export function subscribeChatRetention(listener: () => void): () => void {
  listeners.add(listener);
  return () => { listeners.delete(listener); };
}

export function getChatRetention(): RetentionMap {
  return current;
}

export async function loadChatRetention(): Promise<void> {
  if (loading) return loading;
  loading = (async () => {
    try {
      current = parseRetentionMap(await Storage.getItemAsync(STORAGE_KEY));
      emit();
      await pushRetentionMap();
    } catch (cause: unknown) {
      console.warn('No se pudieron cargar los mensajes temporales.', cause);
    }
  })();
  return loading;
}

export async function saveChatRetention(chatId: string, id: string): Promise<RetentionId> {
  current = setRetention(current, chatId, id);
  emit();
  await Storage.setItemAsync(STORAGE_KEY, JSON.stringify(current)).catch((cause: unknown) => {
    console.warn('No se pudieron guardar los mensajes temporales.', cause);
  });
  await pushRetentionMap();
  return current[chatId] ?? 'off';
}
