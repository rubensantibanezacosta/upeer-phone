import type { ChatList } from '@/domain/chat-lists';
import { sanitizeChatIds, sanitizeChatLists, sanitizeListName } from '@/domain/chat-lists';
import type { ConversationFlags } from '@/domain/conversation-flags';
import type { StarredMap } from '@/domain/starred';

export const REMOTE_SETTINGS_BLOCKS = ['chat', 'message', 'lists', 'blocked', 'profile'] as const;

export type RemoteSettingsBlock = (typeof REMOTE_SETTINGS_BLOCKS)[number];

const ID_FIELD: Partial<Record<RemoteSettingsBlock, string>> = {
  chat: 'chatId',
  message: 'messageId',
  lists: 'listId',
  blocked: 'upeerId',
};

const CONVERSATION_KEYS = ['archived', 'muted', 'pinned'] as const;

export function isRemoteSettingsBlock(value: unknown): value is RemoteSettingsBlock {
  return typeof value === 'string' && (REMOTE_SETTINGS_BLOCKS as readonly string[]).includes(value);
}

export function settingsEntityOf(payload: Record<string, unknown>): string | null {
  const block = payload.block;
  if (!isRemoteSettingsBlock(block)) return null;
  const field = ID_FIELD[block];
  if (!field) return block;
  const id = typeof payload[field] === 'string' ? (payload[field] as string).trim() : '';
  if (!id) return null;
  return `${block}:${id}`;
}

export function parseSettingsClocks(raw: string | null): Record<string, number> {
  if (!raw) return {};
  try {
    const parsed = JSON.parse(raw) as Record<string, unknown>;
    const result: Record<string, number> = {};
    for (const [entity, value] of Object.entries(parsed)) {
      if (entity && typeof value === 'number' && Number.isFinite(value) && value > 0) result[entity] = value;
    }
    return result;
  } catch (cause: unknown) {
    console.warn('Los relojes de ajustes guardados no se pudieron leer.', cause);
    return {};
  }
}

export function parseSettingsCursor(raw: string | null): number {
  const value = Number(raw ?? '0');
  return Number.isFinite(value) && value > 0 ? value : 0;
}

export function mergeConversationFlags(current: ConversationFlags, values: Record<string, unknown>): ConversationFlags | null {
  const merged: ConversationFlags = { ...current };
  let touched = false;
  for (const key of CONVERSATION_KEYS) {
    const candidate = values[key];
    if (typeof candidate === 'boolean' && candidate !== merged[key]) {
      merged[key] = candidate;
      touched = true;
    }
  }
  return touched ? merged : null;
}

export function isEmptyConversationFlags(flags: ConversationFlags): boolean {
  return !flags.archived && !flags.muted && !flags.pinned;
}

export function mergeStarredMessage(map: StarredMap, values: Record<string, unknown>): StarredMap | null {
  const messageId = typeof values.messageId === 'string' ? values.messageId.trim() : '';
  const starred = values.starred;
  if (!messageId || typeof starred !== 'boolean') return null;
  if (Boolean(map[messageId]) === starred) return null;
  const next: StarredMap = { ...map };
  if (starred) next[messageId] = true;
  else delete next[messageId];
  return next;
}

export function applyRemoteChatList(lists: readonly ChatList[], values: Record<string, unknown>): ChatList[] | null {
  const listId = typeof values.listId === 'string' ? values.listId.trim() : '';
  if (!listId) return null;
  if (values.deleted === true) {
    if (!lists.some((list) => list.id === listId)) return null;
    return lists.filter((list) => list.id !== listId);
  }
  const name = sanitizeListName(values.name);
  if (!name) return null;
  const chatIds = sanitizeChatIds(values.chatIds);
  const index = lists.findIndex((list) => list.id === listId);
  if (index >= 0) {
    const current = lists[index];
    if (current.name === name && current.chatIds.join('\u0000') === chatIds.join('\u0000')) return null;
    const next = [...lists];
    next[index] = { id: listId, name, chatIds };
    return next;
  }
  return sanitizeChatLists([...lists, { id: listId, name, chatIds }]);
}
