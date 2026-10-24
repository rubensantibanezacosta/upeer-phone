import { useState } from 'react';
import { Forward } from '@tamagui/lucide-icons-2';
import { Paragraph } from 'tamagui';

import { BottomSheet } from '@/components/bottom-sheet';
import { ContactPicker } from '@/components/contact-picker';
import { NativeRippleButton } from '@/components/native-ripple-button';
import { forwardExcerpt } from '@/domain/forward';
import { useTheme } from '@/hooks/use-theme';
import type { UPeerContact } from '@/storage/contact-store';
import type { ProtocolMessage } from '../../../modules/upeer-transport/src/UPeerTransport.types';
import { sendChat } from '../../../modules/upeer-transport/src/UPeerTransportModule';

type ChatForwardSheetProps = {
  contacts: UPeerContact[];
  message: ProtocolMessage;
  onClose: () => void;
  onForwarded: (count: number, failed: number) => void;
};

export function ChatForwardSheet({ contacts, message, onClose, onForwarded }: ChatForwardSheetProps) {
  const theme = useTheme();
  const [selected, setSelected] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);

  const toggle = (contact: UPeerContact) => {
    setSelected((current) => (current.includes(contact.upeerId)
      ? current.filter((id) => id !== contact.upeerId)
      : [...current, contact.upeerId]));
  };

  const forward = async () => {
    if (selected.length === 0) return;
    setBusy(true);
    let forwarded = 0;
    for (const upeerId of selected) {
      try {
        await sendChat(upeerId, message.content, '');
        forwarded += 1;
      } catch (cause) {
        console.warn(`No se pudo reenviar el mensaje a ${upeerId}.`, cause);
      }
    }
    setBusy(false);
    onForwarded(forwarded, selected.length - forwarded);
  };

  return (
    <BottomSheet icon={<Forward color={theme.accent} size={20} />} onClose={onClose} title="Reenviar mensaje" visible>
      <Paragraph color={theme.placeholder} fontSize={12}>{forwardExcerpt(message.content)}</Paragraph>
      <ContactPicker contacts={contacts} emptyLabel="No tienes contactos a quienes reenviar" onToggle={toggle} searchPlaceholder="Buscar destinatario" selectedIds={selected} />
      <NativeRippleButton backgroundColor={theme.accent} color={theme.onAccent} disabled={busy || selected.length === 0} fontWeight="700" height={46} icon={<Forward color={theme.onAccent} size={17} />} onPress={() => void forward()}>
        {busy ? 'Reenviando…' : selected.length === 0 ? 'Elige un destinatario' : selected.length === 1 ? 'Reenviar a 1 contacto' : `Reenviar a ${selected.length} contactos`}
      </NativeRippleButton>
    </BottomSheet>
  );
}
