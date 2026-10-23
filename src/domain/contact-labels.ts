import type { UPeerContact } from '@/storage/contact-store';

export const CONTACT_STATUS_LABELS: Record<UPeerContact['status'], string> = {
  pending: 'Esperando que acepte…',
  incoming: 'Solicitud de contacto',
  connected: 'En línea',
  offline: 'Desconectado',
  blocked: 'Bloqueado',
};
