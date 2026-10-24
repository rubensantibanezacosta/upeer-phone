import { StyleSheet, View } from 'react-native';
import { MessageSquarePlus, Users } from '@tamagui/lucide-icons-2';
import { SizableText, Text, XStack, YStack } from 'tamagui';

import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';
import { ContactPicker } from '@/components/contact-picker';
import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import type { UPeerContact } from '@/storage/contact-store';

type NewConversationListProps = {
  contacts: UPeerContact[];
  onPick: (contact: UPeerContact) => void;
  onUnavailable: (label: string) => void;
};

export function NewConversationList({ contacts, onPick, onUnavailable }: NewConversationListProps) {
  const theme = useTheme();
  const scale = useTextScale();

  return <YStack gap={12}>
    <NativeRipplePressable accessibilityLabel="Nueva conversación" onPress={() => onUnavailable('La búsqueda de identidades')} style={[styles.actionCard, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
      <XStack alignItems="center" gap={12}>
        <View style={[styles.actionIcon, { backgroundColor: theme.accentSoft }]}><MessageSquarePlus color={theme.accent} size={20} /></View>
        <YStack flex={1}>
          <SizableText color={theme.text} fontSize={scale(14)} fontWeight="600">Nueva conversación</SizableText>
          <Text color={theme.placeholder} fontSize={scale(12)}>Escribe a una identidad que aún no tienes guardada</Text>
        </YStack>
      </XStack>
    </NativeRipplePressable>
    <NativeRipplePressable accessibilityLabel="Nuevo grupo" onPress={() => onUnavailable('Crear grupos')} style={[styles.actionCard, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
      <XStack alignItems="center" gap={12}>
        <View style={[styles.actionIcon, { backgroundColor: theme.backgroundSelected }]}><Users color={theme.placeholder} size={20} /></View>
        <YStack flex={1}>
          <SizableText color={theme.text} fontSize={scale(14)} fontWeight="600">Nuevo grupo</SizableText>
          <Text color={theme.placeholder} fontSize={scale(12)}>Conversación cifrada con varios contactos</Text>
        </YStack>
      </XStack>
    </NativeRipplePressable>
    <YStack gap={8}>
      <Text color={theme.placeholder} fontSize={scale(11)} fontWeight="600">CONTACTOS</Text>
      <ContactPicker contacts={contacts} onPick={onPick} />
    </YStack>
  </YStack>;
}

const styles = StyleSheet.create({
  actionCard: { borderRadius: 14, borderWidth: 1, overflow: 'hidden', padding: 12 },
  actionIcon: { alignItems: 'center', borderRadius: 12, height: 40, justifyContent: 'center', width: 40 },
});
