import Storage from 'expo-sqlite/kv-store';

import { DEFAULT_PREFERENCES, parsePreferences, type Preferences } from '@/domain/preferences';

const STORAGE_KEY = 'upeer.preferences';

let current: Preferences = DEFAULT_PREFERENCES;
let loading: Promise<void> | null = null;
const listeners = new Set<() => void>();

function emit() {
  for (const listener of listeners) listener();
}

export function subscribePreferences(listener: () => void): () => void {
  listeners.add(listener);
  return () => { listeners.delete(listener); };
}

export function getPreferences(): Preferences {
  return current;
}

export async function loadPreferences(): Promise<void> {
  if (loading) return loading;
  loading = Storage.getItemAsync(STORAGE_KEY)
    .then((raw) => {
      current = parsePreferences(raw);
      emit();
    })
    .catch((cause: unknown) => {
      console.warn('No se pudieron cargar las preferencias; se usan las de por defecto', cause);
    });
  return loading;
}

export async function updatePreferences(patch: Partial<Preferences>): Promise<void> {
  current = { ...current, ...patch };
  emit();
  await Storage.setItemAsync(STORAGE_KEY, JSON.stringify(current)).catch((cause: unknown) => {
    console.warn('No se pudieron guardar las preferencias.', cause);
  });
}
