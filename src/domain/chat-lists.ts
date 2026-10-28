export type ChatList = {
  id: string;
  name: string;
  chatIds: string[];
};

export type ChatListChoice = { upeerId: string; title: string };

export const LIST_NAME_MAX_LENGTH = 40;
export const MAX_CHAT_LISTS = 30;
export const MAX_CHATS_PER_LIST = 200;

export function sanitizeListName(raw: unknown): string {
  if (typeof raw !== 'string') return '';
  return raw.trim().replace(/\s+/g, ' ').slice(0, LIST_NAME_MAX_LENGTH);
}

export function sanitizeChatIds(value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  const seen = new Set<string>();
  for (const id of value) {
    if (typeof id !== 'string') continue;
    const trimmed = id.trim();
    if (!trimmed) continue;
    seen.add(trimmed);
    if (seen.size >= MAX_CHATS_PER_LIST) break;
  }
  return [...seen];
}

export function sanitizeChatLists(value: unknown): ChatList[] {
  if (!Array.isArray(value)) return [];
  const lists: ChatList[] = [];
  const usedIds = new Set<string>();
  for (const raw of value) {
    if (!raw || typeof raw !== 'object') continue;
    const candidate = raw as Partial<ChatList>;
    const name = sanitizeListName(candidate.name);
    const id = typeof candidate.id === 'string' ? candidate.id.trim() : '';
    if (!name || !id || usedIds.has(id)) continue;
    usedIds.add(id);
    lists.push({ id, name, chatIds: sanitizeChatIds(candidate.chatIds) });
    if (lists.length >= MAX_CHAT_LISTS) break;
  }
  return lists;
}

export function parseChatLists(raw: string | null): ChatList[] {
  if (!raw) return [];
  try {
    return sanitizeChatLists(JSON.parse(raw));
  } catch (cause: unknown) {
    console.warn('Las listas de chats guardadas no se pudieron leer; se empieza sin ellas.', cause);
    return [];
  }
}

export function nextListId(lists: ChatList[]): string {
  let counter = 1;
  const used = new Set(lists.map((list) => list.id));
  while (used.has(`list-${counter}`)) counter += 1;
  return `list-${counter}`;
}

export function createChatList(lists: ChatList[], name: string): { lists: ChatList[]; list: ChatList | null } {
  const clean = sanitizeListName(name);
  if (!clean || lists.length >= MAX_CHAT_LISTS) return { lists, list: null };
  const list: ChatList = { id: nextListId(lists), name: clean, chatIds: [] };
  return { lists: [...lists, list], list };
}

export function renameChatList(lists: ChatList[], listId: string, name: string): ChatList[] {
  const clean = sanitizeListName(name);
  if (!clean) return lists;
  return lists.map((list) => (list.id === listId ? { ...list, name: clean } : list));
}

export function deleteChatList(lists: ChatList[], listId: string): ChatList[] {
  return lists.filter((list) => list.id !== listId);
}

export function toggleChatInList(lists: ChatList[], listId: string, chatId: string): ChatList[] {
  if (!chatId) return lists;
  return lists.map((list) => {
    if (list.id !== listId) return list;
    if (list.chatIds.includes(chatId)) return { ...list, chatIds: list.chatIds.filter((id) => id !== chatId) };
    if (list.chatIds.length >= MAX_CHATS_PER_LIST) return list;
    return { ...list, chatIds: [...list.chatIds, chatId] };
  });
}

export function findChatList(lists: ChatList[], listId?: string | null): ChatList | null {
  if (!listId) return null;
  return lists.find((list) => list.id === listId) ?? null;
}

export function listsForChat(lists: ChatList[], chatId: string): ChatList[] {
  if (!chatId) return [];
  return lists.filter((list) => list.chatIds.includes(chatId));
}

export function restrictToChatList<T extends { upeerId: string }>(entries: T[], list: ChatList | null): T[] {
  if (!list) return entries;
  const allowed = new Set(list.chatIds);
  return entries.filter((entry) => allowed.has(entry.upeerId));
}

export function describeChatList(list: ChatList): string {
  return list.chatIds.length === 1 ? '1 chat' : `${list.chatIds.length} chats`;
}
