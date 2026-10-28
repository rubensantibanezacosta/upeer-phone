import { parseChatStyleMap, setChatStyle, type ChatStyleId, type ChatStyleMap } from '@/domain/chat-style';
import Storage from 'expo-sqlite/kv-store';

const STORAGE_KEY = 'upeer.chatStyles';

let current: ChatStyleMap = {};
let loading: Promise<void> | null = null;
const listeners = new Set<() => void>();

function emit() {
  for (const listener of listeners) listener();
}

export function subscribeChatStyles(listener: () => void): () => void {
  listeners.add(listener);
  return () => { listeners.delete(listener); };
}

export function getChatStyles(): ChatStyleMap {
  return current;
}

export async function loadChatStyles(): Promise<void> {
  if (loading) return loading;
  loading = (async () => {
    try {
      current = parseChatStyleMap(await Storage.getItemAsync(STORAGE_KEY));
      emit();
    } catch (cause: unknown) {
      console.warn('No se pudieron cargar los estilos de chat.', cause);
    }
  })();
  return loading;
}

export async function saveChatStyle(chatId: string, id: string): Promise<ChatStyleId> {
  current = setChatStyle(current, chatId, id);
  emit();
  await Storage.setItemAsync(STORAGE_KEY, JSON.stringify(current)).catch((cause: unknown) => {
    console.warn('No se pudo guardar el estilo del chat.', cause);
  });
  return current[chatId] ?? 'default';
}
