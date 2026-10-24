const PLACEHOLDER_LENGTH = 12;

export function placeholderName(upeerId: string): string {
  return upeerId.slice(0, PLACEHOLDER_LENGTH);
}

export function isPlaceholderName(name: string | undefined, upeerId: string): boolean {
  const value = name?.trim() ?? '';
  if (!value) return true;
  return value.toLowerCase() === placeholderName(upeerId).toLowerCase();
}

export function resolveContactName(storedName: string | undefined, alias: string | undefined, upeerId: string): string {
  if (!isPlaceholderName(storedName, upeerId)) return (storedName as string).trim();
  const remote = alias?.trim();
  if (remote) return remote;
  return placeholderName(upeerId);
}
