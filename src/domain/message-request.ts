export type RequestContext = {
  contactStatus?: string;
  hasOutgoing: boolean;
  hasIncoming: boolean;
};

export type RequestDecision = 'none' | 'request' | 'auto-accept';

export function decideRequestState({ contactStatus, hasOutgoing, hasIncoming }: RequestContext): RequestDecision {
  if (contactStatus === 'incoming') return 'request';
  if (contactStatus && contactStatus !== 'incoming') return 'none';
  if (hasOutgoing) return 'auto-accept';
  if (hasIncoming) return 'request';
  return 'none';
}
