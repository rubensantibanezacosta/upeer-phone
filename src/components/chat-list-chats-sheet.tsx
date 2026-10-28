import { useMemo, useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { Check, ListChecks } from '@tamagui/lucide-icons-2';
import { SizableText, Text, XStack, YStack } from 'tamagui';

import { BottomSheet } from '@/components/bottom-sheet';
import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { SearchField } from '@/components/search-field';
import type { ChatList, ChatListChoice } from '@/domain/chat-lists';
import { useChatLists } from '@/hooks/use-chat-lists';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';
import { toggleChat } from '@/storage/chat-lists-store';

type ChatListChatsSheetProps = {
  chats: ChatListChoice[];
  list: ChatList | null;
  onClose: () => void;
};

export function ChatListChatsSheet({ chats, list, onClose }: ChatListChatsSheetProps) {
  const theme = useTheme();
  const scale = useTextScale();
  const lists = useChatLists();
  const [query, setQuery] = useState('');

  const filtered = useMemo(() => {
    const needle = query.trim().toLowerCase();
    if (!needle) return chats;
    return chats.filter((chat) => chat.title.toLowerCase().includes(needle) || chat.upeerId.toLowerCase().includes(needle));
  }, [chats, query]);

  const live = useMemo(() => (list ? lists.find((entry) => entry.id === list.id) ?? list : null), [list, lists]);
  const selected = useMemo(() => new Set(live?.chatIds ?? []), [live]);
  const title = live ? `Chats de ${live.name}` : 'Chats';

  return (
    <BottomSheet icon={<ListChecks color={theme.accent} size={20} />} onClose={onClose} title={title} visible={list !== null}>
      <YStack gap={8}>
        <SearchField onChangeText={setQuery} placeholder="Buscar chat" value={query} />
        {filtered.length === 0 ? (
          <Text color={theme.placeholder} fontSize={scale(12)} paddingVertical={16} textAlign="center">Sin chats que coincidan</Text>
        ) : filtered.map((chat) => {
          const active = selected.has(chat.upeerId);
          return (
            <NativeRipplePressable
              accessibilityLabel={`${active ? 'Quitar' : 'Añadir'} ${chat.title} de la lista`}
              accessibilityState={{ selected: active }}
              key={chat.upeerId}
              onPress={() => { if (live) void toggleChat(live.id, chat.upeerId); }}
            >
              <XStack alignItems="center" gap={10} paddingVertical={8}>
                <YStack flex={1}>
                  <SizableText color={theme.text} fontSize={scale(14)} fontWeight="600" numberOfLines={1}>{chat.title}</SizableText>
                  <Text color={theme.placeholder} fontSize={scale(11)} numberOfLines={1}>{chat.upeerId.slice(0, 16)}…</Text>
                </YStack>
                <View style={[styles.check, { backgroundColor: active ? theme.accent : 'transparent', borderColor: active ? theme.accent : theme.border }]}>
                  {active ? <Check color={theme.onAccent} size={14} strokeWidth={3} /> : null}
                </View>
              </XStack>
            </NativeRipplePressable>
          );
        })}
      </YStack>
    </BottomSheet>
  );
}

const styles = StyleSheet.create({
  check: { alignItems: 'center', borderRadius: 10, borderWidth: 1.5, height: 20, justifyContent: 'center', width: 20 },
});
