import { messageSummary } from './contact-card';
import type { ProtocolMessage } from '../../modules/upeer-transport/src/UPeerTransport.types';

export const REPLY_EXCERPT_LENGTH = 80;

export type ReplyPreview = {
  author: string;
  excerpt: string;
};

export function replyExcerpt(content: string): string {
  const summary = messageSummary(content).replace(/\s+/g, ' ').trim();
  if (summary.length <= REPLY_EXCERPT_LENGTH) return summary;
  return `${summary.slice(0, REPLY_EXCERPT_LENGTH - 1).trimEnd()}…`;
}

export function buildReplyPreview(
  original: ProtocolMessage | undefined,
  labels: { mine: string; peer: string },
): ReplyPreview | null {
  if (!original) return null;
  return {
    author: original.isMine ? labels.mine : labels.peer,
    excerpt: replyExcerpt(original.content),
  };
}
