import { Text, YStack } from 'tamagui';

import { ContactPicker } from '@/components/contact-picker';
import { NativeRippleButton } from '@/components/native-ripple-button';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';
import type { UPeerContact } from '@/storage/contact-store';

type GroupInviteFormProps = {
  available: UPeerContact[];
  busy: boolean;
  onInvite: () => void;
  onToggle: (contact: UPeerContact) => void;
  selected: string[];
};

export function GroupInviteForm({ available, busy, onInvite, onToggle, selected }: GroupInviteFormProps) {
  const theme = useTheme();
  const scale = useTextScale();

  return <YStack gap={10}>
    <Text color={theme.placeholder} fontSize={scale(12)}>Solo pueden invitarse contactos ya conectados: la invitación viaja cifrada a su clave.</Text>
    <ContactPicker
      contacts={available}
      emptyLabel="No quedan contactos conectados por invitar"
      onToggle={onToggle}
      searchPlaceholder="Buscar contacto"
      selectedIds={selected}
    />
    <NativeRippleButton
      backgroundColor={theme.accent}
      color={theme.onAccent}
      disabled={busy || selected.length === 0}
      fontWeight="700"
      height={46}
      onPress={onInvite}
    >
      {busy ? 'Invitando…' : `Invitar${selected.length > 0 ? ` (${selected.length})` : ''}`}
    </NativeRippleButton>
  </YStack>;
}
