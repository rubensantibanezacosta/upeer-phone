import { useState } from 'react';
import { Users } from '@tamagui/lucide-icons-2';
import { Input, Text, YStack } from 'tamagui';

import { BottomSheet } from '@/components/bottom-sheet';
import { ContactPicker } from '@/components/contact-picker';
import { NativeRippleButton } from '@/components/native-ripple-button';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';
import type { UPeerContact } from '@/storage/contact-store';
import { createGroup } from '../../modules/upeer-transport/src/UPeerTransportModule';

type GroupComposerSheetProps = {
  contacts: UPeerContact[];
  visible: boolean;
  onClose: () => void;
  onCreated: (groupId: string) => void;
  onError: (message: string) => void;
};

export function GroupComposerSheet({ contacts, visible, onClose, onCreated, onError }: GroupComposerSheetProps) {
  const theme = useTheme();
  const scale = useTextScale();
  const [name, setName] = useState('');
  const [selected, setSelected] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);

  const reset = () => {
    setName('');
    setSelected([]);
    setBusy(false);
  };

  const close = () => {
    if (busy) return;
    reset();
    onClose();
  };

  const toggle = (contact: UPeerContact) =>
    setSelected((current) => (current.includes(contact.upeerId)
      ? current.filter((id) => id !== contact.upeerId)
      : [...current, contact.upeerId]));

  const submit = async () => {
    const title = name.trim();
    if (title.length === 0 || selected.length === 0 || busy) return;
    setBusy(true);
    try {
      const groupId = await createGroup(title, selected);
      reset();
      onCreated(groupId);
    } catch (cause) {
      console.warn('No se pudo crear el grupo.', cause);
      onError(cause instanceof Error ? cause.message : 'No se pudo crear el grupo');
      setBusy(false);
    }
  };

  const ready = contacts.filter((contact) => contact.status === 'connected');

  return <BottomSheet icon={<Users color={theme.accent} size={20} />} onClose={close} title="Nuevo grupo" visible={visible}>
    <YStack gap={6}>
      <Text color={theme.textSecondary} fontSize={scale(11)} fontWeight="600">NOMBRE DEL GRUPO</Text>
      <Input
        accessibilityLabel="Nombre del grupo"
        autoCapitalize="sentences"
        backgroundColor={theme.background}
        borderColor={theme.border}
        color={theme.text}
        onChangeText={setName}
        placeholder="Equipo, familia, proyecto…"
        placeholderTextColor={theme.placeholder}
        value={name}
      />
    </YStack>
    <YStack gap={6}>
      <Text color={theme.textSecondary} fontSize={scale(11)} fontWeight="600">MIEMBROS</Text>
      <Text color={theme.placeholder} fontSize={scale(11)}>
        {selected.length === 0
          ? 'Elige al menos un contacto ya conectado: la invitación viaja cifrada a su clave.'
          : `${selected.length} ${selected.length === 1 ? 'contacto seleccionado' : 'contactos seleccionados'}`}
      </Text>
      <ContactPicker
        contacts={ready}
        emptyLabel="Todavía no hay contactos conectados"
        onToggle={toggle}
        searchPlaceholder="Buscar contacto para el grupo"
        selectedIds={selected}
      />
    </YStack>
    <NativeRippleButton
      accessibilityLabel="Crear grupo"
      backgroundColor={theme.accent}
      color={theme.onAccent}
      disabled={busy || name.trim().length === 0 || selected.length === 0}
      fontWeight="700"
      height={46}
      onPress={() => void submit()}
    >
      {busy ? 'Creando…' : 'Crear grupo'}
    </NativeRippleButton>
  </BottomSheet>;
}
