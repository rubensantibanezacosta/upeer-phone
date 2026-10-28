import type { MessageSearchHit } from '../../modules/upeer-transport/src/UPeerTransportModule';

export type ChatSearchResult = {
  id: string;
  timestamp: number;
};

export const CHAT_SEARCH_PAGE_SIZE = 50;
const EXCERPT_LENGTH = 140;

export function searchExcerpt(content: string): string {
  const raw = content.trim();
  if (raw.startsWith('{') && raw.endsWith('}')) return 'Archivo adjunto';
  const singleLine = raw.replace(/\s+/g, ' ').trim();
  return singleLine.length > EXCERPT_LENGTH ? `${singleLine.slice(0, EXCERPT_LENGTH)}…` : singleLine;
}

export function toChatSearchResults(hits: readonly MessageSearchHit[]): ChatSearchResult[] {
  return hits.map((hit) => ({ id: hit.id, timestamp: hit.timestamp }));
}

export function appendChatSearchResults(current: readonly ChatSearchResult[], page: readonly ChatSearchResult[]): ChatSearchResult[] {
  const seen = new Set(current.map((result) => result.id));
  return [...current, ...page.filter((result) => !seen.has(result.id))];
}

export function searchPageHasMore(pageSize: number): boolean {
  return pageSize >= CHAT_SEARCH_PAGE_SIZE;
}

export type SearchStep =
  | { kind: 'jump'; index: number }
  | { kind: 'load' }
  | { kind: 'none' };

export function resolveSearchStep(index: number, count: number, hasMore: boolean, delta: number): SearchStep {
  if (count === 0) return { kind: 'none' };
  const next = index + delta;
  if (next >= 0 && next < count) return { kind: 'jump', index: next };
  if (next >= count && hasMore) return { kind: 'load' };
  if (next < 0 && hasMore) return { kind: 'none' };
  return { kind: 'jump', index: ((next % count) + count) % count };
}

export function describeSearchPosition(index: number, count: number, more = false): string {
  if (count === 0) return 'Sin resultados';
  const position = Math.min(Math.max(index, 0), count - 1) + 1;
  return `${position} de ${count}${more ? '+' : ''}`;
}
