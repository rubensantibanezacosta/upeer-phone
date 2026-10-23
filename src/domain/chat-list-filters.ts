export type ChatListFilter = 'all' | 'unread';

type FilterableEntry = {
  upeerId: string;
  title: string;
  preview: string;
  unread: number;
};

export const CHAT_LIST_FILTERS: { id: ChatListFilter; label: string }[] = [
  { id: 'all', label: 'Todos' },
  { id: 'unread', label: 'No leídos' },
];

export function matchesChatQuery(entry: FilterableEntry, query: string): boolean {
  const needle = query.trim().toLowerCase();
  if (!needle) return true;
  return entry.title.toLowerCase().includes(needle)
    || entry.upeerId.toLowerCase().includes(needle)
    || entry.preview.toLowerCase().includes(needle);
}

export function filterChatEntries<T extends FilterableEntry>(entries: T[], filter: ChatListFilter, query: string): T[] {
  return entries.filter((entry) => matchesChatQuery(entry, query) && (filter !== 'unread' || entry.unread > 0));
}

export function isChatFilterActive(filter: ChatListFilter, query: string): boolean {
  return filter !== 'all' || query.trim().length > 0;
}
