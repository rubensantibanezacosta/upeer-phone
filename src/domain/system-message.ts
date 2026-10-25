export const SYSTEM_MESSAGE_PREFIX = '__SYS__|';

const SEPARATOR = ' · ';

export function isSystemMessage(content: string): boolean {
  return content.startsWith(SYSTEM_MESSAGE_PREFIX);
}

export function systemMessageText(content: string): string | null {
  if (!isSystemMessage(content)) return null;
  const body = content.slice(SYSTEM_MESSAGE_PREFIX.length).trim();
  if (!body) return null;
  const parts = body.split(SEPARATOR).map((part) => part.trim());
  if (parts.length === 3 && parts[0] && parts[1] && /^\d{1,20}$/.test(parts[2])) {
    return `${parts[0]}${SEPARATOR}${parts[1]}`;
  }
  return body;
}
