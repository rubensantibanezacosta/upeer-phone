import { useMemo, useState } from 'react';
import { ScrollView, StyleSheet } from 'react-native';
import { Search } from '@tamagui/lucide-icons-2';
import { Input, SizableText, Text, XStack, YStack } from 'tamagui';

import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { ProfileAvatar } from '@/components/profile-avatar';
import { useTheme } from '@/hooks/use-theme';
import type { UPeerContact } from '@/storage/contact-store';

type ContactPickerProps = {
  contacts: UPeerContact[];
  emptyLabel?: string;
  excludeUpeerId?: string;
  onPick: (contact: UPeerContact) => void;
  searchPlaceholder?: string;
};

export function ContactPicker({ contacts, emptyLabel = 'Sin contactos que coincidan', excludeUpeerId, onPick, searchPlaceholder = 'Buscar contacto' }: ContactPickerProps) {
  const theme = useTheme();
  const [query, setQuery] = useState('');

  const filtered = useMemo(() => {
    const needle = query.trim().toLowerCase();
    const available = excludeUpeerId ? contacts.filter((contact) => contact.upeerId !== excludeUpeerId) : contacts;
    if (!needle) return available;
    return available.filter((contact) => contact.name.toLowerCase().includes(needle) || contact.upeerId.toLowerCase().includes(needle));
  }, [contacts, excludeUpeerId, query]);

  return (
    <YStack gap={8}>
      <XStack alignItems="center" backgroundColor={theme.backgroundElement} borderColor={theme.border} borderRadius={12} borderWidth={1} gap={8} paddingHorizontal={10}>
        <Search color={theme.placeholder} size={16} />
        <Input backgroundColor="transparent" borderWidth={0} color={theme.text} flex={1} fontSize={13} height={40} onChangeText={setQuery} placeholder={searchPlaceholder} placeholderTextColor={theme.placeholder} value={query} />
      </XStack>
      <ScrollView contentContainerStyle={styles.listContent} keyboardShouldPersistTaps="handled" style={styles.list}>
        {filtered.length === 0
          ? <Text color={theme.placeholder} fontSize={12} paddingVertical={16} textAlign="center">{emptyLabel}</Text>
          : filtered.map((contact) => (
            <NativeRipplePressable accessibilityLabel={`Elegir a ${contact.name}`} key={contact.upeerId} onPress={() => onPick(contact)}>
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
  );
}

const styles = StyleSheet.create({
  list: { maxHeight: 240 },
  listContent: { paddingBottom: 4 },
});
