import type { ProtocolMessage } from '../../modules/upeer-transport/src/UPeerTransport.types';

export type MessageStatusIcon = 'failed' | 'pending' | 'sent' | 'delivered';

export function statusIconKind(status: ProtocolMessage['status']): MessageStatusIcon {
  if (status === 'failed') return 'failed';
  if (status === 'pending') return 'pending';
  if (status === 'sent' || status === 'vaulted') return 'sent';
  return 'delivered';
}

export function statusIconIsRead(status: ProtocolMessage['status']): boolean {
  return status === 'read';
}
