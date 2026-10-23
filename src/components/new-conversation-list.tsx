import { useMemo, useState } from 'react';
import { ScrollView, StyleSheet, View } from 'react-native';
import { MessageSquarePlus, Search, Users } from '@tamagui/lucide-icons-2';
import { Input, SizableText, Text, XStack, YStack } from 'tamagui';

import { useTheme } from '@/hooks/use-theme';
import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { ProfileAvatar } from '@/components/profile-avatar';
import type { UPeerContact } from '@/storage/contact-store';

type NewConversationListProps = {
  contacts: UPeerContact[];
  onPick: (contact: UPeerContact) => void;
  onUnavailable: (label: string) => void;
};

export function NewConversationList({ contacts, onPick, onUnavailable }: NewConversationListProps) {
  const theme = useTheme();
  const [query, setQuery] = useState('');

  const filtered = useMemo(() => {
    const needle = query.trim().toLowerCase();
    if (!needle) return contacts;
    return contacts.filter((contact) => contact.name.toLowerCase().includes(needle) || contact.upeerId.toLowerCase().includes(needle));
  }, [contacts, query]);

  return <YStack gap={12}>
    <NativeRipplePressable accessibilityLabel="Nueva conversación" onPress={() => onUnavailable('La búsqueda de identidades')} style={[styles.actionCard, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
      <XStack alignItems="center" gap={12}>
        <View style={[styles.actionIcon, { backgroundColor: theme.accentSoft }]}><MessageSquarePlus color={theme.accent} size={20} /></View>
        <YStack flex={1}>
          <SizableText color={theme.text} fontSize={14} fontWeight="600">Nueva conversación</SizableText>
          <Text color={theme.placeholder} fontSize={12}>Escribe a una identidad que aún no tienes guardada</Text>
        </YStack>
      </XStack>
    </NativeRipplePressable>
    <NativeRipplePressable accessibilityLabel="Nuevo grupo" onPress={() => onUnavailable('Crear grupos')} style={[styles.actionCard, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
      <XStack alignItems="center" gap={12}>
        <View style={[styles.actionIcon, { backgroundColor: theme.backgroundSelected }]}><Users color={theme.placeholder} size={20} /></View>
        <YStack flex={1}>
          <SizableText color={theme.text} fontSize={14} fontWeight="600">Nuevo grupo</SizableText>
          <Text color={theme.placeholder} fontSize={12}>Conversación cifrada con varios contactos</Text>
        </YStack>
      </XStack>
    </NativeRipplePressable>
    <YStack gap={8}>
      <Text color={theme.placeholder} fontSize={11} fontWeight="600">CONTACTOS</Text>
      <XStack alignItems="center" backgroundColor={theme.backgroundElement} borderColor={theme.border} borderRadius={12} borderWidth={1} gap={8} paddingHorizontal={10}>
        <Search color={theme.placeholder} size={16} />
        <Input backgroundColor="transparent" borderWidth={0} color={theme.text} flex={1} fontSize={13} height={40} onChangeText={setQuery} placeholder="Buscar contacto" placeholderTextColor={theme.placeholder} value={query} />
      </XStack>
      <ScrollView contentContainerStyle={styles.listContent} keyboardShouldPersistTaps="handled" style={styles.list}>
        {filtered.length === 0
          ? <Text color={theme.placeholder} fontSize={12} paddingVertical={16} textAlign="center">Sin contactos que coincidan</Text>
          : filtered.map((contact) => (
            <NativeRipplePressable accessibilityLabel={`Abrir conversación con ${contact.name}`} key={contact.upeerId} onPress={() => onPick(contact)}>
              <XStack alignItems="center" gap={10} paddingVertical={8}>
                <ProfileAvatar name={contact.name} size={36} />
                <YStack flex={1}>
                  <SizableText color={theme.text} fontSize={14} fontWeight="600" numberOfLines={1}>{contact.name}</SizableText>
                  <Text color={theme.placeholder} fontSize={11} numberOfLines={1}>{contact.upeerId}</Text>
                </YStack>
              </XStack>
            </NativeRipplePressable>
          ))}
      </ScrollView>
    </YStack>
  </YStack>;
}

const styles = StyleSheet.create({
  actionCard: { borderRadius: 14, borderWidth: 1, overflow: 'hidden', padding: 12 },
  actionIcon: { alignItems: 'center', borderRadius: 12, height: 40, justifyContent: 'center', width: 40 },
  list: { maxHeight: 240 },
  listContent: { paddingBottom: 4 },
});
