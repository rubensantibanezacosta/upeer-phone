export type StarredMap = Record<string, true>;

export type StarrableMessage = {
  id: string;
  isMine: boolean;
  timestamp: number;
  content?: string;
  deleted?: boolean;
};

export function isStarred(map: StarredMap, id?: string | null): boolean {
  return Boolean(id && map[id]);
}

export function toggleStarred(map: StarredMap, id: string): StarredMap {
  if (!id) return map;
  const next: StarredMap = { ...map };
  if (next[id]) delete next[id];
  else next[id] = true;
  return next;
}

export function setStarred(map: StarredMap, ids: readonly string[], value: boolean): StarredMap {
  const next: StarredMap = { ...map };
  for (const id of ids) {
    if (!id) continue;
    if (value) next[id] = true;
    else delete next[id];
  }
  return next;
}

export function allStarred(map: StarredMap, ids: readonly string[]): boolean {
  const valid = ids.filter(Boolean);
  return valid.length > 0 && valid.every((id) => Boolean(map[id]));
}

export function sanitizeStarred(value: unknown): StarredMap {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return {};

  const result: StarredMap = {};
  for (const [id, flag] of Object.entries(value as Record<string, unknown>)) {
    if (id && flag === true) result[id] = true;
  }
  return result;
}

export function parseStarred(raw?: string | null): StarredMap {
  if (!raw) return {};
  try {
    return sanitizeStarred(JSON.parse(raw));
  } catch {
    return {};
  }
}

export function collectStarred<T extends StarrableMessage>(messages: readonly T[], map: StarredMap): T[] {
  return messages
    .filter((message) => message.deleted !== true && Boolean(map[message.id]))
    .map((message, index) => ({ message, index }))
    .sort((left, right) => (right.message.timestamp === left.message.timestamp
      ? left.index - right.index
      : right.message.timestamp - left.message.timestamp))
    .map((entry) => entry.message);
}

export function countStarred(messages: readonly StarrableMessage[], map: StarredMap): number {
  return collectStarred(messages, map).length;
}
