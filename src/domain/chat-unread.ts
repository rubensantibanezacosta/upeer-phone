import type { ProtocolConversation } from '../../modules/upeer-transport/src/UPeerTransport.types';

export const MAX_BADGE_COUNT = 99;

export function countUnreadChats(conversations: readonly ProtocolConversation[]): number {
  return conversations.filter((conversation) => conversation.lastIsMine !== true && conversation.unread > 0).length;
}

export function unreadConversationIds(conversations: readonly ProtocolConversation[]): string[] {
  return conversations.filter((conversation) => conversation.unread > 0).map((conversation) => conversation.upeerId);
}

export function formatBadgeCount(count: number): string | undefined {
  if (!Number.isFinite(count) || count < 1) return undefined;
  return count > MAX_BADGE_COUNT ? `${MAX_BADGE_COUNT}+` : String(Math.trunc(count));
}
