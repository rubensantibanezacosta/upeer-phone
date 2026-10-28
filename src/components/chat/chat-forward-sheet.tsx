import { useState } from 'react';
import { Forward } from '@tamagui/lucide-icons-2';
import { Paragraph } from 'tamagui';

import { BottomSheet } from '@/components/bottom-sheet';
import { ContactPicker } from '@/components/contact-picker';
import { NativeRippleButton } from '@/components/native-ripple-button';
import { forwardExcerpt } from '@/domain/forward';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';
import type { UPeerContact } from '@/storage/contact-store';
import type { ProtocolMessage } from '../../../modules/upeer-transport/src/UPeerTransport.types';
import { sendChat } from '../../../modules/upeer-transport/src/UPeerTransportModule';

type ChatForwardSheetProps = {
  contacts: UPeerContact[];
  messages: ProtocolMessage[];
  onClose: () => void;
  onForwarded: (count: number, failed: number) => void;
};

export function ChatForwardSheet({ contacts, messages, onClose, onForwarded }: ChatForwardSheetProps) {
  const theme = useTheme();
  const scale = useTextScale();
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
    let failed = 0;
    for (const upeerId of selected) {
      for (const message of messages) {
        try {
          await sendChat(upeerId, message.content, '');
          forwarded += 1;
        } catch (cause) {
          failed += 1;
          console.warn(`No se pudo reenviar el mensaje a ${upeerId}.`, cause);
        }
      }
    }
    setBusy(false);
    onForwarded(forwarded, failed);
  };

  const title = messages.length === 1 ? 'Reenviar mensaje' : `Reenviar ${messages.length} mensajes`;
  const preview = messages.map((message) => forwardExcerpt(message.content)).join('\n');

  return (
    <BottomSheet icon={<Forward color={theme.accent} size={20} />} onClose={onClose} title={title} visible>
      <Paragraph color={theme.placeholder} fontSize={scale(12)} numberOfLines={4}>{preview}</Paragraph>
      <ContactPicker contacts={contacts} emptyLabel="No tienes contactos a quienes reenviar" onToggle={toggle} searchPlaceholder="Buscar destinatario" selectedIds={selected} />
      <NativeRippleButton backgroundColor={theme.accent} color={theme.onAccent} disabled={busy || selected.length === 0} fontWeight="700" height={46} icon={<Forward color={theme.onAccent} size={17} />} onPress={() => void forward()}>
        {busy ? 'Reenviando…' : selected.length === 0 ? 'Elige un destinatario' : selected.length === 1 ? 'Reenviar a 1 contacto' : `Reenviar a ${selected.length} contactos`}
      </NativeRippleButton>
    </BottomSheet>
  );
}
