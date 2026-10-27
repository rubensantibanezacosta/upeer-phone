import type { ChatListFilter } from './chat-list-filters';

export type ChipChatState = {
  unread: number;
  favorite?: boolean;
  group?: boolean;
};

export type ChipContactState = {
  upeerId: string;
  favorite?: boolean;
  blocked?: boolean;
};

export type ChipGroupState = {
  groupId: string;
};

export type ChipChatLookup = ReadonlyMap<string, ChipChatState>;

export function matchesChatChip(chat: ChipChatState, chip: ChatListFilter): boolean {
  if (chip === 'unread') return chat.unread > 0;
  if (chip === 'favorites') return chat.favorite === true;
  if (chip === 'groups') return chat.group === true;
  return true;
}

export function isSearchableContact(contact: ChipContactState): boolean {
  return contact.blocked !== true;
}

export function matchesContactChip(contact: ChipContactState, chip: ChatListFilter, chats: ChipChatLookup): boolean {
  if (!isSearchableContact(contact)) return false;
  if (chip === 'favorites') return contact.favorite === true;
  if (chip === 'unread') return (chats.get(contact.upeerId)?.unread ?? 0) > 0;
  return true;
}

export function matchesGroupChip(group: ChipGroupState, chip: ChatListFilter, chats: ChipChatLookup): boolean {
  const chat = chats.get(group.groupId);
  if (chip === 'unread') return (chat?.unread ?? 0) > 0;
  if (chip === 'favorites') return chat?.favorite === true;
  return true;
}

export function matchesMessageChip(message: { chatUpeerId: string }, chip: ChatListFilter, chats: ChipChatLookup): boolean {
  if (chip === 'all') return true;
  const chat = chats.get(message.chatUpeerId);
  if (!chat) return false;
  return matchesChatChip(chat, chip);
}
