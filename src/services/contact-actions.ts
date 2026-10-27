import type { ContactDraft } from '@/components/contact-form-sheet';
import { registerPeer as registerNativePeer } from '../../modules/upeer-transport/src/UPeerTransportModule';
import { saveContact } from '@/storage/contact-store';

export async function saveContactDraft(identityUpeerId: string, draft: ContactDraft): Promise<void> {
  if (draft.upeerId === identityUpeerId.toLowerCase()) throw new Error('No puedes añadirte como contacto.');
  await saveContact({ upeerId: draft.upeerId, address: draft.address, name: draft.name, publicKey: null, status: 'pending', createdAt: Date.now() });
  try {
    await registerNativePeer(draft.upeerId, draft.address, '', '');
  } catch (cause) {
    console.warn('El contacto se guardó, pero el servicio nativo no lo registró.', cause);
  }
}
