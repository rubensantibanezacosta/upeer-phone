import type { ChatListFilter } from './chat-list-filters';
import { searchExcerpt } from './chat-search';
import { systemMessageText } from './system-message';

export type SearchTarget = { kind: 'chat'; upeerId: string; messageId?: string } | { kind: 'contact'; upeerId: string } | { kind: 'group'; groupId: string };

export type SearchRow = {
  key: string;
  title: string;
  subtitle: string;
  target: SearchTarget;
};

export type SearchSectionId = 'chats' | 'contacts' | 'groups' | 'messages';

export type SearchSection = {
  id: SearchSectionId;
  title: string;
  total: number;
  rows: SearchRow[];
};

export type SearchChat = {
  upeerId: string;
  title: string;
  preview: string;
  unread: number;
  timestamp: number;
  group?: boolean;
  favorite?: boolean;
  pinned?: boolean;
};

export type SearchContact = {
  upeerId: string;
  name: string;
  address: string;
  blocked?: boolean;
  favorite?: boolean;
};

export type SearchGroup = {
  groupId: string;
  name: string;
  members: string[];
  updatedAt: number;
};

export type SearchMessage = {
  id: string;
  chatUpeerId: string;
  chatTitle: string;
  content: string;
  timestamp: number;
  isMine: boolean;
};

export type SearchData = {
  chats: SearchChat[];
  contacts: SearchContact[];
  groups: SearchGroup[];
  messages: SearchMessage[];
};

export const CONTACT_RESULT_LIMIT = 5;
export const MESSAGE_RESULT_LIMIT = 10;

export function normalizeSearchText(value: string): string {
  return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
}

export function searchMessageText(content: string): string | null {
  const raw = content.trim();
  if (raw.startsWith('{') && raw.endsWith('}')) return null;
  return systemMessageText(raw) ?? searchExcerpt(raw);
}

function chatMatchesChip(chat: SearchChat, chip: ChatListFilter): boolean {
  if (chip === 'unread') return chat.unread > 0;
  if (chip === 'favorites') return chat.favorite === true;
  if (chip === 'groups') return chat.group === true;
  return true;
}

function groupMatchesChip(group: SearchGroup, chip: ChatListFilter, chats: Map<string, SearchChat>): boolean {
  const chat = chats.get(group.groupId);
  if (chip === 'unread') return (chat?.unread ?? 0) > 0;
  if (chip === 'favorites') return chat?.favorite === true;
  return true;
}

function messageMatchesChip(message: SearchMessage, chip: ChatListFilter, chats: Map<string, SearchChat>): boolean {
  if (chip === 'all') return true;
  const chat = chats.get(message.chatUpeerId);
  if (!chat) return false;
  return chatMatchesChip(chat, chip);
}

function contactMatchesChip(contact: SearchContact, chip: ChatListFilter, chats: Map<string, SearchChat>): boolean {
  if (chip === 'favorites') return contact.favorite === true;
  if (chip === 'unread') return (chats.get(contact.upeerId)?.unread ?? 0) > 0;
  return true;
}

function contactRank(contact: SearchContact, needle: string): number {
  const name = normalizeSearchText(contact.name);
  if (name === needle) return 0;
  if (name.startsWith(needle)) return 1;
  return 2;
}

export function buildSearchSections(query: string, chip: ChatListFilter, data: SearchData): SearchSection[] {
  const needle = normalizeSearchText(query.trim());
  if (!needle) return [];

  const chats = new Map(data.chats.map((chat) => [chat.upeerId, chat]));
  const chatRows = data.chats
    .filter((chat) => chatMatchesChip(chat, chip)
      && (normalizeSearchText(chat.title).includes(needle) || chat.upeerId.toLowerCase().includes(needle)))
    .sort((left, right) => Number(right.pinned === true) - Number(left.pinned === true) || right.timestamp - left.timestamp)
    .map<SearchRow>((chat) => ({
      key: `chat:${chat.upeerId}`,
      title: chat.title,
      subtitle: chat.preview,
      target: { kind: 'chat', upeerId: chat.upeerId },
    }));
  const listedChats = new Set(chatRows.map((row) => (row.target.kind === 'chat' ? row.target.upeerId : '')));

  const matchingContacts = data.contacts
    .filter((contact) => contact.blocked !== true)
    .filter((contact) => normalizeSearchText(contact.name).includes(needle)
      || contact.upeerId.toLowerCase().startsWith(needle)
      || normalizeSearchText(contact.address).includes(needle));
  const contactRows = chip === 'groups'
    ? []
    : matchingContacts
      .filter((contact) => contactMatchesChip(contact, chip, chats))
      .filter((contact) => !listedChats.has(contact.upeerId))
      .sort((left, right) => contactRank(left, needle) - contactRank(right, needle) || left.name.localeCompare(right.name))
      .map<SearchRow>((contact) => ({
        key: `contact:${contact.upeerId}`,
        title: contact.name,
        subtitle: `${contact.upeerId.slice(0, 12)} · ${contact.address}`,
        target: { kind: 'contact', upeerId: contact.upeerId },
      }));

  const ownedGroups = data.groups
    .filter((group) => groupMatchesChip(group, chip, chats) && normalizeSearchText(group.name).includes(needle))
    .map<{ group: SearchGroup; subtitle: string }>((group) => ({ group, subtitle: `${group.members.length} miembros` }));
  const sharedGroups = matchingContacts.flatMap((contact) => data.groups
    .filter((group) => groupMatchesChip(group, chip, chats) && group.members.includes(contact.upeerId))
    .map((group) => ({ group, subtitle: `Compartido con ${contact.name}` })));
  const listedGroups = new Set<string>();
  const groupRows = [...ownedGroups, ...sharedGroups]
    .filter((entry) => {
      const key = entry.group.groupId;
      if (listedChats.has(key) || listedGroups.has(key)) return false;
      listedGroups.add(key);
      return true;
    })
    .sort((left, right) => right.group.updatedAt - left.group.updatedAt)
    .map<SearchRow>((entry) => ({
      key: `group:${entry.group.groupId}`,
      title: entry.group.name,
      subtitle: entry.subtitle,
      target: { kind: 'group', groupId: entry.group.groupId },
    }));

  const messageRows = data.messages
    .filter((message) => messageMatchesChip(message, chip, chats))
    .filter((message) => normalizeSearchText(searchMessageText(message.content) ?? '').includes(needle))
    .sort((left, right) => right.timestamp - left.timestamp)
    .map<SearchRow>((message) => ({
      key: `message:${message.chatUpeerId}:${message.id}`,
      title: message.chatTitle,
      subtitle: searchMessageText(message.content) ?? '',
      target: { kind: 'chat', upeerId: message.chatUpeerId, messageId: message.id },
    }));

  const sections: SearchSection[] = [
    { id: 'chats', title: 'Chats', total: chatRows.length, rows: chatRows },
    { id: 'contacts', title: 'Contactos', total: contactRows.length, rows: contactRows.slice(0, CONTACT_RESULT_LIMIT) },
    { id: 'groups', title: 'Grupos en común', total: groupRows.length, rows: groupRows },
    { id: 'messages', title: 'Mensajes', total: messageRows.length, rows: messageRows.slice(0, MESSAGE_RESULT_LIMIT) },
  ];
  return chip === 'groups' ? sections.filter((section) => section.id !== 'contacts') : sections;
}

export function visibleSearchSections(sections: SearchSection[]): SearchSection[] {
  return sections.filter((section) => section.rows.length > 0);
}
