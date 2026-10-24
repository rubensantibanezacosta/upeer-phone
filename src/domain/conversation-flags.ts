export type ConversationFlags = {
  archived: boolean;
  muted: boolean;
  pinned: boolean;
};

export type ConversationFlag = keyof ConversationFlags;
export type ConversationFlagsMap = Record<string, ConversationFlags>;

export const EMPTY_CONVERSATION_FLAGS: ConversationFlags = { archived: false, muted: false, pinned: false };

export function flagsFor(map: ConversationFlagsMap, upeerId: string): ConversationFlags {
  return map[upeerId] ?? EMPTY_CONVERSATION_FLAGS;
}

export function isEmptyFlags(flags: ConversationFlags): boolean {
  return !flags.archived && !flags.muted && !flags.pinned;
}

export function toggleFlag(map: ConversationFlagsMap, upeerId: string, flag: ConversationFlag): ConversationFlagsMap {
  const current = flagsFor(map, upeerId);
  const next: ConversationFlags = { ...current, [flag]: !current[flag] };
  if (isEmptyFlags(next)) {
    const rest = { ...map };
    delete rest[upeerId];
    return rest;
  }
  return { ...map, [upeerId]: next };
}

export function parseConversationFlags(raw: string | null): ConversationFlagsMap {
  if (!raw) return {};
  try {
    const parsed = JSON.parse(raw) as Record<string, Partial<ConversationFlags>>;
    const result: ConversationFlagsMap = {};
    for (const [upeerId, value] of Object.entries(parsed)) {
      if (!upeerId || typeof value !== 'object' || value === null) continue;
      const flags: ConversationFlags = {
        archived: value.archived === true,
        muted: value.muted === true,
        pinned: value.pinned === true,
      };
      if (!isEmptyFlags(flags)) result[upeerId] = flags;
    }
    return result;
  } catch {
    console.warn('Los ajustes de conversación guardados no se pudieron leer; se empieza sin ellos');
    return {};
  }
}

export function mutedPeerIds(map: ConversationFlagsMap): string[] {
  return Object.entries(map).filter(([, flags]) => flags.muted).map(([upeerId]) => upeerId);
}

export function orderByPinned<T extends { upeerId: string }>(entries: T[], map: ConversationFlagsMap): T[] {
  const pinned: T[] = [];
  const rest: T[] = [];
  for (const entry of entries) {
    if (flagsFor(map, entry.upeerId).pinned) pinned.push(entry);
    else rest.push(entry);
  }
  return pinned.length === 0 ? entries : [...pinned, ...rest];
}
const FLAG_MESSAGES: Record<ConversationFlag, { on: string; off: string }> = {
  archived: { on: 'Chat archivado', off: 'Chat desarchivado' },
  muted: { on: 'Notificaciones silenciadas', off: 'Notificaciones activadas' },
  pinned: { on: 'Chat fijado', off: 'Chat desfijado' },
};

export function flagMessage(flag: ConversationFlag, enabled: boolean): string {
  return enabled ? FLAG_MESSAGES[flag].on : FLAG_MESSAGES[flag].off;
}
