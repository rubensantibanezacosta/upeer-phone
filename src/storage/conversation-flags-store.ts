import Storage from 'expo-sqlite/kv-store';

import { flagsFor, mutedPeerIds, parseConversationFlags, toggleFlag, type ConversationFlag, type ConversationFlags, type ConversationFlagsMap } from '@/domain/conversation-flags';
import { isEmptyConversationFlags, mergeConversationFlags } from '@/domain/settings-sync';
import { setMutedPeers } from '../../modules/upeer-transport/src/UPeerTransportModule';
import { publishChatFlags } from '@/storage/settings-sync-client';

const STORAGE_KEY = 'upeer.conversationFlags';

let current: ConversationFlagsMap = {};
let loading: Promise<void> | null = null;
const listeners = new Set<() => void>();

function emit() {
  for (const listener of listeners) listener();
}

async function pushMutedPeers(): Promise<void> {
  await setMutedPeers(JSON.stringify(mutedPeerIds(current))).catch((cause: unknown) => {
    console.warn('El servicio no recibió la lista de conversaciones silenciadas.', cause);
  });
}

export function subscribeConversationFlags(listener: () => void): () => void {
  listeners.add(listener);
  return () => { listeners.delete(listener); };
}

export function getConversationFlags(): ConversationFlagsMap {
  return current;
}

export function getFlagsFor(upeerId: string): ConversationFlags {
  return flagsFor(current, upeerId);
}

export async function loadConversationFlags(): Promise<void> {
  if (loading) return loading;
  loading = (async () => {
    try {
      current = parseConversationFlags(await Storage.getItemAsync(STORAGE_KEY));
      emit();
      await pushMutedPeers();
    } catch (cause: unknown) {
      console.warn('No se pudieron cargar los ajustes de conversación.', cause);
    }
  })();
  return loading;
}

export async function setConversationFlag(upeerId: string, flag: ConversationFlag): Promise<void> {
  current = toggleFlag(current, upeerId, flag);
  emit();
  await Storage.setItemAsync(STORAGE_KEY, JSON.stringify(current)).catch((cause: unknown) => {
    console.warn('No se pudieron guardar los ajustes de conversación.', cause);
  });
  await pushMutedPeers();
  await publishChatFlags(upeerId, { ...flagsFor(current, upeerId) });
}

export function applyRemoteConversationFlags(values: Record<string, unknown>): void {
  const upeerId = typeof values.chatId === 'string' ? values.chatId.trim() : '';
  if (!upeerId) return;
  const merged = mergeConversationFlags(flagsFor(current, upeerId), values);
  if (!merged) return;
  const next = { ...current };
  if (isEmptyConversationFlags(merged)) delete next[upeerId];
  else next[upeerId] = merged;
  current = next;
  emit();
  Storage.setItemAsync(STORAGE_KEY, JSON.stringify(current)).catch((cause: unknown) => {
    console.warn('No se pudieron guardar los ajustes de conversación sincronizados.', cause);
  });
  pushMutedPeers().catch((cause: unknown) => {
    console.warn('El servicio no recibió la lista de conversaciones silenciadas.', cause);
  });
}
