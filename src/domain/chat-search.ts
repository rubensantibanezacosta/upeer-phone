import type { ProtocolMessage } from '../../modules/upeer-transport/src/UPeerTransport.types';

export type ChatSearchResult = {
  id: string;
  excerpt: string;
  isMine: boolean;
  timestamp: number;
};

const MAX_RESULTS = 50;
const EXCERPT_LENGTH = 140;

const normalize = (value: string) => value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();

export function searchExcerpt(content: string): string {
  const raw = content.trim();
  if (raw.startsWith('{') && raw.endsWith('}')) return 'Archivo adjunto';
  const singleLine = raw.replace(/\s+/g, ' ').trim();
  return singleLine.length > EXCERPT_LENGTH ? `${singleLine.slice(0, EXCERPT_LENGTH)}…` : singleLine;
}

export function searchChatMessages(messages: readonly ProtocolMessage[], query: string): ChatSearchResult[] {
  const needle = normalize(query.trim());
  if (!needle) return [];
  const matches = messages.filter((message) => normalize(message.content).includes(needle));
  return matches.slice(-MAX_RESULTS).reverse().map((message) => ({
    excerpt: searchExcerpt(message.content),
    id: message.id,
    isMine: message.isMine,
    timestamp: message.timestamp,
  }));
}

export function describeSearchPosition(index: number, count: number): string {
  if (count === 0) return 'Sin resultados';
  const position = Math.min(Math.max(index, 0), count - 1) + 1;
  return `${position} de ${count}`;
}
