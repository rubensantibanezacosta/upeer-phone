export type PeerAction = 'call' | 'video' | 'block' | 'delete' | 'favorite' | 'clear' | 'close' | 'mute' | 'export';

export function isSelfConversation(upeerId: string | undefined | null, ownUpeerId: string | undefined | null): boolean {
  if (!upeerId || !ownUpeerId) return false;
  return upeerId.toLowerCase() === ownUpeerId.toLowerCase();
}

const SELF_FORBIDDEN: ReadonlySet<PeerAction> = new Set<PeerAction>(['call', 'video', 'block', 'delete', 'favorite']);

export function canActOnPeer(action: PeerAction, isSelf: boolean): boolean {
  if (!isSelf) return true;
  return !SELF_FORBIDDEN.has(action);
}
