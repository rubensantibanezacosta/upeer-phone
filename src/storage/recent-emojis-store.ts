import Storage from 'expo-sqlite/kv-store';

import { pushRecentEmoji } from '@/domain/emoji-catalog';

const STORAGE_KEY = 'upeer.recentEmojis';

let current: string[] = [];
let loading: Promise<void> | null = null;
const listeners = new Set<() => void>();

function emit() {
  for (const listener of listeners) listener();
}

export function subscribeRecentEmojis(listener: () => void): () => void {
  listeners.add(listener);
  return () => { listeners.delete(listener); };
}

export function getRecentEmojis(): string[] {
  return current;
}

export async function loadRecentEmojis(): Promise<void> {
  if (loading) return loading;
  loading = Storage.getItemAsync(STORAGE_KEY)
    .then((raw) => {
      current = parseRecentEmojis(raw);
      emit();
    })
    .catch((cause: unknown) => {
      console.warn('No se pudieron cargar los emojis recientes.', cause);
    });
  return loading;
}

export async function rememberEmoji(emoji: string): Promise<void> {
  current = pushRecentEmoji(current, emoji);
  emit();
  await Storage.setItemAsync(STORAGE_KEY, JSON.stringify(current)).catch((cause: unknown) => {
    console.warn('No se pudieron guardar los emojis recientes.', cause);
  });
}

function parseRecentEmojis(raw: string | null): string[] {
  if (!raw) return [];
  try {
    const parsed: unknown = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed.filter((item): item is string => typeof item === 'string') : [];
  } catch (cause) {
    console.warn('Los emojis recientes guardados no eran válidos; se empieza de cero.', cause);
    return [];
  }
}
