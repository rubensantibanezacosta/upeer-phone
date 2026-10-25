export type RequestContext = {
  contactStatus?: string;
  hasOutgoing: boolean;
  hasIncoming: boolean;
  isGroup?: boolean;
};

export type RequestDecision = 'none' | 'request' | 'auto-accept';

export function decideRequestState({ contactStatus, hasOutgoing, hasIncoming, isGroup = false }: RequestContext): RequestDecision {
  if (isGroup) return 'none';
  if (contactStatus === 'incoming') return 'request';
  if (contactStatus === 'pending') return hasOutgoing || hasIncoming ? 'auto-accept' : 'none';
  if (contactStatus) return 'none';
  if (hasOutgoing) return 'auto-accept';
  if (hasIncoming) return 'request';
  return 'none';
}
