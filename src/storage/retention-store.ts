import Storage from 'expo-sqlite/kv-store';

import { parseRetentionMap, setRetention, type RetentionId, type RetentionMap } from '@/domain/retention';
import { getRetentionStatus, sendRetention, setRetentionMap } from '../../modules/upeer-transport/src/UPeerTransportModule';

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

async function readNativeRetention(): Promise<RetentionMap> {
  try {
    const raw = await getRetentionStatus();
    const parsed = JSON.parse(raw) as { map?: unknown };
    return parseRetentionMap(JSON.stringify(parsed?.map ?? {}));
  } catch (cause: unknown) {
    console.warn('El servicio no devolvió los plazos de conservación.', cause);
    return {};
  }
}

export async function loadChatRetention(): Promise<void> {
  if (loading) return loading;
  loading = (async () => {
    try {
      const stored = parseRetentionMap(await Storage.getItemAsync(STORAGE_KEY));
      current = { ...stored, ...(await readNativeRetention()) };
      emit();
      await pushRetentionMap();
    } catch (cause: unknown) {
      console.warn('No se pudieron cargar los mensajes temporales.', cause);
    } finally {
      loading = null;
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
  await announceRetention(chatId, current[chatId] ?? 'off');
  return current[chatId] ?? 'off';
}

async function announceRetention(chatId: string, id: RetentionId): Promise<void> {
  if (chatId.startsWith('grp-')) return;
  await sendRetention(chatId, id).catch((cause: unknown) => {
    console.warn('El otro extremo no recibió el plazo de conservación.', cause);
  });
}
