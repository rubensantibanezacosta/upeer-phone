import { ScrollView, StyleSheet } from 'react-native';
import { ListPlus } from '@tamagui/lucide-icons-2';
import { Text } from 'tamagui';

import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { CHAT_LIST_FILTERS, type ChatListFilter } from '@/domain/chat-list-filters';
import type { ChatList } from '@/domain/chat-lists';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

type ChatFilterChipsProps = {
  activeListId: string | null;
  filter: ChatListFilter;
  lists: ChatList[];
  onManageLists: () => void;
  onSelectFilter: (filter: ChatListFilter) => void;
  onSelectList: (listId: string) => void;
};

export function ChatFilterChips({ activeListId, filter, lists, onManageLists, onSelectFilter, onSelectList }: ChatFilterChipsProps) {
  const theme = useTheme();
  const scale = useTextScale();
  const chip = (label: string, active: boolean, onPress: () => void) => (
    <NativeRipplePressable
      accessibilityRole="button"
      accessibilityState={{ selected: active }}
      key={label}
      onPress={onPress}
      style={[styles.chip, { backgroundColor: active ? theme.accent : theme.backgroundElement }]}
    >
      <Text color={active ? theme.onAccent : theme.text} fontSize={scale(13)} fontWeight={active ? '700' : '400'}>{label}</Text>
    </NativeRipplePressable>
  );

  return (
    <ScrollView contentContainerStyle={styles.row} horizontal showsHorizontalScrollIndicator={false} style={styles.scroll}>
      {CHAT_LIST_FILTERS.map((item) => chip(item.label, activeListId === null && filter === item.id, () => onSelectFilter(item.id)))}
      {lists.length > 0 ? <Text alignSelf="center" color={theme.placeholder} fontSize={scale(12)}>·</Text> : null}
      {lists.map((list) => chip(list.name, activeListId === list.id, () => onSelectList(list.id)))}
      <NativeRipplePressable accessibilityLabel="Gestionar listas" accessibilityRole="button" onPress={onManageLists} style={[styles.chip, styles.manage, { borderColor: theme.border }]}>
        <ListPlus color={theme.placeholder} size={15} />
      </NativeRipplePressable>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  chip: { alignItems: 'center', borderRadius: 16, flexDirection: 'row', gap: 6, height: 32, justifyContent: 'center', paddingHorizontal: 14 },
  manage: { borderWidth: 1, paddingHorizontal: 10 },
  row: { alignItems: 'center', flexDirection: 'row', gap: 8 },
  scroll: { flexGrow: 0 },
});
