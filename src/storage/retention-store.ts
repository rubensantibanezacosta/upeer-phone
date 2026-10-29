import Storage from 'expo-sqlite/kv-store';

import { parseRetentionMap, setRetention, type RetentionId, type RetentionMap } from '@/domain/retention';
import { getRetentionStatus, sendRetention, setRetentionMap } from '../../modules/upeer-transport/src/UPeerTransportModule';

const STORAGE_KEY = 'upeer.chatRetention';

let current: RetentionMap = {};
let loading: Promise<void> | null = null;
let pushing = 0;
const listeners = new Set<() => void>();

function emit() {
  for (const listener of listeners) listener();
}

function sameMap(a: RetentionMap, b: RetentionMap): boolean {
  const keys = new Set([...Object.keys(a), ...Object.keys(b)]);
  for (const key of keys) {
    if ((a[key] ?? 'off') !== (b[key] ?? 'off')) return false;
  }
  return true;
}

async function pushRetentionMap(): Promise<void> {
  pushing += 1;
  try {
    await setRetentionMap(JSON.stringify(current)).catch((cause: unknown) => {
      console.warn('El servicio no recibió los mensajes temporales.', cause);
    });
  } finally {
    pushing -= 1;
  }
}

export function subscribeChatRetention(listener: () => void): () => void {
  listeners.add(listener);
  return () => { listeners.delete(listener); };
}

export function getChatRetention(): RetentionMap {
  return current;
}

async function readNativeRetention(): Promise<RetentionMap | null> {
  try {
    const raw = await getRetentionStatus();
    const parsed = JSON.parse(raw) as { map?: unknown };
    return parseRetentionMap(JSON.stringify(parsed?.map ?? {}));
  } catch (cause: unknown) {
    console.warn('El servicio no devolvió los plazos de conservación.', cause);
    return null;
  }
}

export async function loadChatRetention(): Promise<void> {
  if (loading) return loading;
  loading = (async () => {
    try {
      const stored = parseRetentionMap(await Storage.getItemAsync(STORAGE_KEY));
      current = { ...stored, ...((await readNativeRetention()) ?? {}) };
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
  pushing += 1;
  try {
    current = setRetention(current, chatId, id);
    emit();
    await Storage.setItemAsync(STORAGE_KEY, JSON.stringify(current)).catch((cause: unknown) => {
      console.warn('No se pudieron guardar los mensajes temporales.', cause);
    });
    await pushRetentionMap();
  } finally {
    pushing -= 1;
  }
  await announceRetention(chatId, current[chatId] ?? 'off');
  return current[chatId] ?? 'off';
}

export async function adoptNativeRetention(): Promise<void> {
  if (pushing > 0 || loading) return;
  const native = await readNativeRetention();
  if (!native) return;
  if (sameMap(native, current)) return;
  current = native;
  emit();
  await Storage.setItemAsync(STORAGE_KEY, JSON.stringify(current)).catch((cause: unknown) => {
    console.warn('No se pudieron guardar los mensajes temporales sincronizados.', cause);
  });
}

async function announceRetention(chatId: string, id: RetentionId): Promise<void> {
  if (chatId.startsWith('grp-')) return;
  await sendRetention(chatId, id).catch((cause: unknown) => {
    console.warn('El otro extremo no recibió el plazo de conservación.', cause);
  });
}
