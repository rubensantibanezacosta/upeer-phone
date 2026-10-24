import { useEffect, useState } from 'react';
import { Share2 } from '@tamagui/lucide-icons-2';

import { BottomSheet } from '@/components/bottom-sheet';
import { ContactPicker } from '@/components/contact-picker';
import { useTheme } from '@/hooks/use-theme';
import { getContacts, type UPeerContact } from '@/storage/contact-store';
import { sendChat } from '../../../modules/upeer-transport/src/UPeerTransportModule';
import { serializeContactCard } from '@/domain/contact-card';

type ContactShareSheetProps = {
  contact: UPeerContact | null;
  onClose: () => void;
  onSent: (target: UPeerContact) => void;
  onError: (message: string) => void;
  visible: boolean;
};

export function ContactShareSheet({ contact, onClose, onError, onSent, visible }: ContactShareSheetProps) {
  const theme = useTheme();
  const [contacts, setContacts] = useState<UPeerContact[]>([]);

  useEffect(() => {
    if (!visible) return;
    let active = true;
    getContacts()
      .then((list) => {
        if (active) setContacts(list);
      })
      .catch((cause: unknown) => console.warn('No se pudo cargar la agenda para compartir.', cause));
    return () => {
      active = false;
    };
  }, [visible]);

  const share = (target: UPeerContact) => {
    if (!contact) return;
    const content = serializeContactCard({
      address: contact.address,
      name: contact.name,
      publicKey: contact.publicKey ?? undefined,
      upeerId: contact.upeerId,
    });
    onClose();
    sendChat(target.upeerId, content)
      .then(() => onSent(target))
      .catch((cause: unknown) => {
        onError(cause instanceof Error ? cause.message : 'No se pudo compartir el contacto.');
      });
  };

  return (
    <BottomSheet icon={<Share2 color={theme.accent} size={20} />} onClose={onClose} title="Compartir contacto" visible={visible}>
      {contact ? <ContactPicker contacts={contacts} emptyLabel="No hay otros contactos a quien enviarla" excludeUpeerId={contact.upeerId} onPick={share} searchPlaceholder="Buscar destinatario" /> : null}
    </BottomSheet>
  );
}
