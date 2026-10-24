import { useMemo, useState } from 'react';
import { StyleSheet, View, ScrollView } from 'react-native';
import { Check, Search } from '@tamagui/lucide-icons-2';
import { Input, SizableText, Text, XStack, YStack } from 'tamagui';

import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { ProfileAvatar } from '@/components/profile-avatar';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';
import type { UPeerContact } from '@/storage/contact-store';

type ContactPickerProps = {
  contacts: UPeerContact[];
  emptyLabel?: string;
  excludeUpeerId?: string;
  onPick?: (contact: UPeerContact) => void;
  onToggle?: (contact: UPeerContact) => void;
  searchPlaceholder?: string;
  selectedIds?: string[];
};

export function ContactPicker({ contacts, emptyLabel = 'Sin contactos que coincidan', excludeUpeerId, onPick, onToggle, searchPlaceholder = 'Buscar contacto', selectedIds = [] }: ContactPickerProps) {
  const theme = useTheme();
  const scale = useTextScale();
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
        <Input backgroundColor="transparent" borderWidth={0} color={theme.text} flex={1} fontSize={scale(13)} height={40} onChangeText={setQuery} placeholder={searchPlaceholder} placeholderTextColor={theme.placeholder} value={query} />
      </XStack>
      <ScrollView contentContainerStyle={styles.listContent} keyboardShouldPersistTaps="handled" style={styles.list}>
        {filtered.length === 0
          ? <Text color={theme.placeholder} fontSize={scale(12)} paddingVertical={16} textAlign="center">{emptyLabel}</Text>
          : filtered.map((contact) => {
            const selected = selectedIds.includes(contact.upeerId);
            return (
              <NativeRipplePressable
                accessibilityLabel={`${onToggle ? (selected ? 'Quitar' : 'Añadir') : 'Elegir'} a ${contact.name}`}
                accessibilityState={onToggle ? { selected } : undefined}
                key={contact.upeerId}
                onPress={() => (onToggle ? onToggle(contact) : onPick?.(contact))}
              >
                <XStack alignItems="center" gap={10} paddingVertical={8}>
                  <ProfileAvatar name={contact.name} size={36} />
                  <YStack flex={1}>
                    <SizableText color={theme.text} fontSize={scale(14)} fontWeight="600" numberOfLines={1}>{contact.name}</SizableText>
                    <Text color={theme.placeholder} fontSize={scale(11)} numberOfLines={1}>{contact.upeerId}</Text>
                  </YStack>
                  {onToggle ? <View style={[styles.check, { backgroundColor: selected ? theme.accent : 'transparent', borderColor: selected ? theme.accent : theme.border }]}>{selected ? <Check color={theme.onAccent} size={14} strokeWidth={3} /> : null}</View> : null}
                </XStack>
              </NativeRipplePressable>
            );
          })}
      </ScrollView>
    </YStack>
  );
}

const styles = StyleSheet.create({
  list: { maxHeight: 240 },
  listContent: { paddingBottom: 4 },
  check: { alignItems: 'center', borderRadius: 11, borderWidth: 1.5, height: 22, justifyContent: 'center', width: 22 },
});
