import type { UPeerContact } from '@/storage/contact-store';

export const CONTACT_STATUS_LABELS: Record<UPeerContact['status'], string> = {
  pending: 'Pendiente',
  incoming: 'Solicitud de contacto',
  connected: 'En línea',
  offline: 'Desconectado',
  blocked: 'Bloqueado',
};

export function presenceText(presence: string | null | undefined, status: UPeerContact['status']): string {
  if (presence) return presence;
  if (status === 'connected') return CONTACT_STATUS_LABELS.offline;
  return CONTACT_STATUS_LABELS[status];
}
