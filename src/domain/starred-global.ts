import type { StarredMap } from '@/domain/starred';

export type StarredGlobalRow = {
  id: string;
  chatId: string;
  content: string;
  timestamp: number;
  isMine: boolean;
};

export type StarredGlobalGroup = {
  chatId: string;
  name: string;
  rows: StarredGlobalRow[];
};

export type StarrableGlobalMessage = {
  id: string;
  upeerId: string;
  content: string;
  timestamp: number;
  isMine: boolean;
  deleted?: boolean;
};

const MAX_NAME = 32;

export function shortenedChatName(chatId: string): string {
  if (chatId.length <= MAX_NAME) return chatId;
  return `${chatId.slice(0, MAX_NAME)}…`;
}

export function toStarredGlobalRows(messages: readonly StarrableGlobalMessage[], map: StarredMap): StarredGlobalRow[] {
  return messages
    .filter((message) => Boolean(map[message.id]) && message.deleted !== true && Boolean(message.upeerId))
    .map((message) => ({
      id: message.id,
      chatId: message.upeerId,
      content: message.content,
      timestamp: Number.isFinite(message.timestamp) ? message.timestamp : 0,
      isMine: message.isMine === true,
    }))
    .sort((left, right) => (right.timestamp === left.timestamp ? left.id.localeCompare(right.id) : right.timestamp - left.timestamp));
}

export function groupStarredByChat(rows: readonly StarredGlobalRow[], titles: Record<string, string>): StarredGlobalGroup[] {
  const groups = new Map<string, StarredGlobalGroup>();

  for (const row of rows) {
    const existing = groups.get(row.chatId);
    if (existing) {
      existing.rows.push(row);
      continue;
    }
    const title = titles[row.chatId];
    groups.set(row.chatId, {
      chatId: row.chatId,
      name: title && title.trim().length > 0 ? title.trim() : shortenedChatName(row.chatId),
      rows: [row],
    });
  }

  return [...groups.values()]
    .map((group) => ({
      ...group,
      rows: group.rows.sort((left, right) => (right.timestamp === left.timestamp ? left.id.localeCompare(right.id) : right.timestamp - left.timestamp)),
    }))
    .sort((left, right) => {
      const newestLeft = left.rows[0]?.timestamp ?? 0;
      const newestRight = right.rows[0]?.timestamp ?? 0;
      return newestRight - newestLeft;
    });
}

export function starredTotal(groups: readonly StarredGlobalGroup[]): number {
  return groups.reduce((total, group) => total + group.rows.length, 0);
}

export function describeStarredTotal(total: number): string {
  if (total === 0) return 'Ningún mensaje destacado';
  return total === 1 ? '1 mensaje destacado' : `${total} mensajes destacados`;
}
