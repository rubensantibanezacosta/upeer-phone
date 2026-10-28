let pending: string | null = null;
const listeners = new Set<() => void>();

function emit() {
  for (const listener of listeners) listener();
}

export function requestChatSearch(upeerId: string): void {
  pending = upeerId.toLowerCase();
  emit();
}

export function getPendingChatSearch(): string | null {
  return pending;
}

export function consumeChatSearch(upeerId: string): boolean {
  const target = upeerId.toLowerCase();
  if (pending !== target) return false;
  pending = null;
  return true;
}

export function subscribeChatSearch(listener: () => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}
