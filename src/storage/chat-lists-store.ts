import Storage from 'expo-sqlite/kv-store';

import {
  createChatList,
  deleteChatList,
  parseChatLists,
  renameChatList,
  toggleChatInList,
  type ChatList,
} from '@/domain/chat-lists';

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
  return list;
}

export async function renameList(listId: string, name: string): Promise<void> {
  await persist(renameChatList(current, listId, name));
}

export async function deleteList(listId: string): Promise<void> {
  await persist(deleteChatList(current, listId));
}

export async function toggleChat(listId: string, chatId: string): Promise<void> {
  await persist(toggleChatInList(current, listId, chatId));
}
