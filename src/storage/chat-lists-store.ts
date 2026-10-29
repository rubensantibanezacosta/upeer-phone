import Storage from 'expo-sqlite/kv-store';

import {
  createChatList,
  deleteChatList,
  parseChatLists,
  renameChatList,
  toggleChatInList,
  type ChatList,
} from '@/domain/chat-lists';
import { applyRemoteChatList } from '@/domain/settings-sync';
import { publishChatList, publishChatListDeleted } from '@/storage/settings-sync-client';

const STORAGE_KEY = 'upeer.chatLists';

let current: ChatList[] = [];
let loading: Promise<void> | null = null;
const listeners = new Set<() => void>();

function emit() {
  for (const listener of listeners) listener();
}

async function persist(next: ChatList[]): Promise<void> {
  current = next;
  emit();
  await Storage.setItemAsync(STORAGE_KEY, JSON.stringify(next)).catch((cause: unknown) => {
    console.warn('No se pudieron guardar las listas de chats.', cause);
  });
}

export function subscribeChatLists(listener: () => void): () => void {
  listeners.add(listener);
  return () => { listeners.delete(listener); };
}

export function getChatLists(): ChatList[] {
  return current;
}

export async function loadChatLists(): Promise<void> {
  if (loading) return loading;
  loading = (async () => {
    try {
      current = parseChatLists(await Storage.getItemAsync(STORAGE_KEY));
      emit();
    } catch (cause: unknown) {
      console.warn('No se pudieron cargar las listas de chats.', cause);
    }
  })();
  return loading;
}

export async function createList(name: string): Promise<ChatList | null> {
  const { lists, list } = createChatList(current, name);
  if (!list) return null;
  await persist(lists);
  await publishChatList(list);
  return list;
}

export async function renameList(listId: string, name: string): Promise<void> {
  const lists = renameChatList(current, listId, name);
  await persist(lists);
  await publishListFrom(lists, listId);
}

export async function deleteList(listId: string): Promise<void> {
  await persist(deleteChatList(current, listId));
  await publishChatListDeleted(listId);
}

export async function toggleChat(listId: string, chatId: string): Promise<void> {
  const lists = toggleChatInList(current, listId, chatId);
  await persist(lists);
  await publishListFrom(lists, listId);
}

async function publishListFrom(lists: readonly ChatList[], listId: string): Promise<void> {
  const list = lists.find((entry) => entry.id === listId);
  if (!list) return;
  await publishChatList(list);
}

export function applyRemoteList(values: Record<string, unknown>): void {
  const next = applyRemoteChatList(current, values);
  if (!next) return;
  current = next;
  emit();
  Storage.setItemAsync(STORAGE_KEY, JSON.stringify(current)).catch((cause: unknown) => {
    console.warn('No se pudieron guardar las listas de chats sincronizadas.', cause);
  });
}
