export type ChatListFilter = 'all' | 'unread' | 'favorites' | 'archived';

type FilterableEntry = {
  upeerId: string;
  title: string;
  preview: string;
  unread: number;
  archived?: boolean;
  favorite?: boolean;
};

export const CHAT_LIST_FILTERS: { id: ChatListFilter; label: string }[] = [
  { id: 'all', label: 'Todos' },
  { id: 'unread', label: 'No leídos' },
  { id: 'favorites', label: 'Favoritos' },
  { id: 'archived', label: 'Archivados' },
];

export function matchesChatQuery(entry: FilterableEntry, query: string): boolean {
  const needle = query.trim().toLowerCase();
  if (!needle) return true;
  return entry.title.toLowerCase().includes(needle)
    || entry.upeerId.toLowerCase().includes(needle)
    || entry.preview.toLowerCase().includes(needle);
}

export function filterChatEntries<T extends FilterableEntry>(entries: T[], filter: ChatListFilter, query: string): T[] {
  return entries.filter((entry) => matchesChatQuery(entry, query)
    && (filter === 'archived' ? entry.archived === true : entry.archived !== true)
    && (filter !== 'unread' || entry.unread > 0)
    && (filter !== 'favorites' || entry.favorite === true));
}

export function isChatFilterActive(filter: ChatListFilter, query: string): boolean {
  return filter !== 'all' || query.trim().length > 0;
}
