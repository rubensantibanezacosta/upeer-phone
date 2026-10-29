import type { ConversationFlags } from '@/domain/conversation-flags';

export const REMOTE_SETTINGS_BLOCKS = ['chat'] as const;

export type RemoteSettingsBlock = (typeof REMOTE_SETTINGS_BLOCKS)[number];

const CONVERSATION_KEYS = ['archived', 'muted', 'pinned'] as const;

export function isRemoteSettingsBlock(value: unknown): value is RemoteSettingsBlock {
  return typeof value === 'string' && (REMOTE_SETTINGS_BLOCKS as readonly string[]).includes(value);
}

export function settingsEntityOf(payload: Record<string, unknown>): string | null {
  const block = payload.block;
  if (!isRemoteSettingsBlock(block)) return null;
  const chatId = typeof payload.chatId === 'string' ? payload.chatId.trim() : '';
  if (!chatId) return null;
  return `${block}:${chatId}`;
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
