import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { FlatList, KeyboardAvoidingView, Platform, StyleSheet, View, type ListRenderItemInfo, type NativeScrollEvent, type NativeSyntheticEvent } from 'react-native';
import { router, useLocalSearchParams } from 'expo-router';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';
import { CircleAlert } from '@tamagui/lucide-icons-2';
import { Text, XStack } from 'tamagui';

import { ChatComposer } from '@/components/chat/chat-composer';
import { ChatDateSeparator } from '@/components/chat/chat-date-separator';
import { ChatEmptyState } from '@/components/chat/chat-empty-state';
import { ChatHeader } from '@/components/chat/chat-header';
import { ChatMessageBubble } from '@/components/chat/chat-message-bubble';
import { ChatScrollButton } from '@/components/chat/chat-scroll-button';
import { useSnackbar } from '@/components/snackbar';
import { buildChatTimeline, toInvertedChatTimeline, type ChatTimelineEntry } from '@/domain/chat-timeline';
import { useTheme } from '@/hooks/use-theme';
import { getContacts, type UPeerContact } from '@/storage/contact-store';
import type { ProtocolMessage } from '../../../../modules/upeer-transport/src/UPeerTransport.types';
import { getMessages, getConversations, markConversationRead, sendChat } from '../../../../modules/upeer-transport/src/UPeerTransportModule';

const MESSAGE_LIMIT = 200;
const LATEST_OFFSET = 24;
const SCROLL_BUTTON_OFFSET = 180;

const keyExtractor = (entry: ChatTimelineEntry) => entry.message.id;

export default function ChatRoute() {
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  const { showSnackbar } = useSnackbar();
  const { upeerId } = useLocalSearchParams<{ upeerId: string }>();
  const listRef = useRef<FlatList<ChatTimelineEntry>>(null);
  const atLatestRef = useRef(true);
  const previousCountRef = useRef(0);
  const [messages, setMessages] = useState<ProtocolMessage[]>([]);
  const [alias, setAlias] = useState('');
  const [contactStatus, setContactStatus] = useState<UPeerContact['status']>('offline');
  const [text, setText] = useState('');
  const [error, setError] = useState('');
  const [showScrollButton, setShowScrollButton] = useState(false);

  const refresh = useCallback(async () => {
    if (!upeerId) return;
    const [nextMessages, contacts, conversations] = await Promise.all([getMessages(upeerId, MESSAGE_LIMIT), getContacts(), getConversations()]);
    setMessages(nextMessages);
    const contact = contacts.find((item) => item.upeerId === upeerId);
    const conversationAlias = conversations.find((conversation) => conversation.upeerId === upeerId)?.alias;
    setAlias(contact?.name || conversationAlias || upeerId.slice(0, 12));
    setContactStatus(contact?.status ?? 'offline');
    await markConversationRead(upeerId);
  }, [upeerId]);

  useEffect(() => {
    if (!upeerId) return;
    const initial = setTimeout(() => {
      void refresh().catch((cause: unknown) => {
        setError(cause instanceof Error ? cause.message : 'No se pudo cargar la conversación.');
      });
    }, 0);
    const timer = setInterval(() => {
      void refresh().catch((cause: unknown) => console.warn('No se pudo actualizar el chat.', cause));
    }, 1500);
    return () => {
      clearTimeout(initial);
      clearInterval(timer);
    };
  }, [refresh, upeerId]);

  const timeline = useMemo(() => toInvertedChatTimeline(buildChatTimeline(messages)), [messages]);

  useEffect(() => {
    const nextCount = messages.length;
    const previousCount = previousCountRef.current;
    previousCountRef.current = nextCount;
    if (nextCount === 0 || nextCount <= previousCount) return;
    if (!atLatestRef.current && !timeline[0]?.message.isMine) return;
    listRef.current?.scrollToOffset({ animated: false, offset: 0 });
    setShowScrollButton(false);
  }, [messages.length, timeline]);

  const submit = async () => {
    const content = text.trim();
    if (!content || !upeerId) return;
    setText('');
    setError('');
    try {
      await sendChat(upeerId, content);
      await refresh();
    } catch (cause) {
      setText(content);
      setError(cause instanceof Error ? cause.message : 'No se pudo enviar el mensaje.');
    }
  };

  const showUnavailable = useCallback((label: string) => {
    showSnackbar(`${label}: pendiente en el roadmap`);
  }, [showSnackbar]);

  const handleScroll = useCallback((event: NativeSyntheticEvent<NativeScrollEvent>) => {
    const offset = Math.max(0, event.nativeEvent.contentOffset.y);
    atLatestRef.current = offset < LATEST_OFFSET;
    setShowScrollButton(offset > SCROLL_BUTTON_OFFSET);
  }, []);

  const scrollToLatest = useCallback(() => {
    atLatestRef.current = true;
    setShowScrollButton(false);
    listRef.current?.scrollToOffset({ animated: true, offset: 0 });
  }, []);

  const renderItem = useCallback(({ item }: ListRenderItemInfo<ChatTimelineEntry>) => (
    <View>
      {item.showDateSeparator ? <ChatDateSeparator timestamp={item.message.timestamp} /> : null}
      <ChatMessageBubble entry={item} />
    </View>
  ), []);

  return (
    <View style={styles.root}>
      <SafeAreaView edges={['top', 'bottom']} style={[styles.root, { backgroundColor: theme.background }]}>
        <ChatHeader alias={alias || 'Conversación'} onBack={() => router.back()} onUnavailable={showUnavailable} status={contactStatus} upeerId={upeerId ?? ''} />
        <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined} style={styles.root}>
          <View style={styles.timeline}>
            {timeline.length === 0 ? <ChatEmptyState /> : (
              <FlatList
                ref={listRef}
                contentContainerStyle={styles.listContent}
                data={timeline}
                initialNumToRender={24}
                inverted
                keyboardDismissMode="interactive"
                keyboardShouldPersistTaps="handled"
                keyExtractor={keyExtractor}
                maxToRenderPerBatch={16}
                onScroll={handleScroll}
                renderItem={renderItem}
                scrollEventThrottle={16}
                showsVerticalScrollIndicator={false}
                style={styles.list}
                updateCellsBatchingPeriod={40}
                windowSize={7}
              />
            )}
            {showScrollButton && timeline.length > 0 ? <ChatScrollButton onPress={scrollToLatest} /> : null}
          </View>
          {!!error && (
            <XStack alignItems="center" backgroundColor={theme.backgroundElement} borderTopColor={theme.border} borderTopWidth={StyleSheet.hairlineWidth} gap={7} paddingHorizontal={14} paddingVertical={7}>
              <CircleAlert color={theme.danger} size={15} />
              <Text color={theme.danger} flex={1} fontSize={12}>{error}</Text>
            </XStack>
          )}
          <ChatComposer onChange={setText} onSend={() => { void submit(); }} onUnavailable={showUnavailable} value={text} />
        </KeyboardAvoidingView>
      </SafeAreaView>
      {insets.bottom > 0 ? (
        <View pointerEvents="none" style={[styles.nativeBarBackground, { backgroundColor: theme.backgroundElement, height: insets.bottom }]} />
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  timeline: { flex: 1, position: 'relative' },
  list: { flex: 1 },
  listContent: { flexGrow: 1, paddingHorizontal: 12, paddingVertical: 16 },
  nativeBarBackground: { bottom: 0, left: 0, position: 'absolute', right: 0 },
});
