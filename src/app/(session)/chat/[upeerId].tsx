import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { FlatList, KeyboardAvoidingView, Platform, StyleSheet, View, type ListRenderItemInfo, type NativeScrollEvent, type NativeSyntheticEvent } from 'react-native';
import { router, useLocalSearchParams } from 'expo-router';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';
import { CircleAlert, Pencil, X } from '@tamagui/lucide-icons-2';
import { Text, XStack } from 'tamagui';

import { ChatComposer } from '@/components/chat/chat-composer';
import { ChatDateSeparator } from '@/components/chat/chat-date-separator';
import { ChatEmptyState } from '@/components/chat/chat-empty-state';
import { ChatHeader } from '@/components/chat/chat-header';
import { ChatMessageActions } from '@/components/chat/chat-message-actions';
import { ChatMessageBubble } from '@/components/chat/chat-message-bubble';
import { ChatRequestScreen } from '@/components/chat/chat-request-screen';
import { ChatScrollButton } from '@/components/chat/chat-scroll-button';
import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { useSnackbar } from '@/components/snackbar';
import { buildChatTimeline, toInvertedChatTimeline, type ChatTimelineEntry } from '@/domain/chat-timeline';
import { summarizeReactions } from '@/domain/chat-reactions';
import { decideRequestState } from '@/domain/message-request';
import { useMessageActions } from '@/hooks/use-message-actions';
import { useCopyToClipboard } from '@/hooks/use-copy-to-clipboard';
import { useTheme } from '@/hooks/use-theme';
import { getContacts, saveContact, type UPeerContact } from '@/storage/contact-store';
import type { ProtocolMessage } from '../../../../modules/upeer-transport/src/UPeerTransport.types';
import { editChat, getMessages, getConversations, markConversationRead, sendChat, sendTyping } from '../../../../modules/upeer-transport/src/UPeerTransportModule';

const MESSAGE_LIMIT = 200;
const LATEST_OFFSET = 24;
const SCROLL_BUTTON_OFFSET = 180;
const TYPING_INTERVAL_MS = 3000;

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
  const [isRequest, setIsRequest] = useState(false);
  const [requestReceivedAt, setRequestReceivedAt] = useState(0);
  const [text, setText] = useState('');
  const [editing, setEditing] = useState<ProtocolMessage | null>(null);
  const [error, setError] = useState('');
  const [showScrollButton, setShowScrollButton] = useState(false);
  const [peerTyping, setPeerTyping] = useState(false);
  const typingSentAtRef = useRef(0);


  const refresh = useCallback(async () => {
    if (!upeerId) return;
    const [nextMessages, contacts, conversations] = await Promise.all([getMessages(upeerId, MESSAGE_LIMIT), getContacts(), getConversations()]);
    setMessages(nextMessages);
    const contact = contacts.find((item) => item.upeerId === upeerId);
    const ownConversation = conversations.find((conversation) => conversation.upeerId === upeerId);
    const conversationAlias = ownConversation?.alias;
    setPeerTyping(ownConversation?.typing === true);
    setAlias(contact?.name || conversationAlias || upeerId.slice(0, 12));
    const decision = decideRequestState({
      contactStatus: contact?.status,
      hasIncoming: nextMessages.some((message) => !message.isMine),
      hasOutgoing: nextMessages.some((message) => message.isMine),
    });
    setContactStatus(contact?.status ?? 'offline');
    setIsRequest(decision === 'request');
    setRequestReceivedAt(nextMessages.find((message) => !message.isMine)?.timestamp ?? 0);
    if (decision === 'auto-accept') {
      await saveContact({ address: '', createdAt: Date.now(), name: contact?.name || conversationAlias || upeerId.slice(0, 12), publicKey: null, status: 'connected', upeerId });
      setContactStatus('connected');
    }
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

  const handleChange = useCallback((value: string) => {
    setText(value);
    if (!upeerId || !value.trim()) return;
    const now = Date.now();
    if (now - typingSentAtRef.current < TYPING_INTERVAL_MS) return;
    typingSentAtRef.current = now;
    sendTyping(upeerId).catch((cause: unknown) => {
      console.warn('No se pudo enviar el indicador de escritura.', cause);
    });
  }, [upeerId]);

  const submit = async () => {
    const content = text.trim();
    if (!content || !upeerId) return;
    const currentEdit = editing;
    setText('');
    setEditing(null);
    setError('');
    try {
      if (currentEdit) {
        await editChat(upeerId, currentEdit.id, content);
      } else {
        await sendChat(upeerId, content);
      }
      await refresh();
    } catch (cause) {
      setText(content);
      setEditing(currentEdit);
      setError(cause instanceof Error ? cause.message : 'No se pudo enviar el mensaje.');
    }
  };

  const startEditing = useCallback((message: ProtocolMessage) => {
    setEditing(message);
    setText(message.content);
    setError('');
  }, []);

  const cancelEditing = useCallback(() => {
    setEditing(null);
    setText('');
  }, []);

  const copy = useCopyToClipboard();
  const messageActions = useMessageActions(upeerId, {
    onChanged: refresh,
    onError: setError,
  });
  const { open: openActions, close: closeActions, remove: removeMessage, selected: actionMessage, selfUpeerId, toggleReaction } = messageActions;

  const editFromActions = useCallback((message: ProtocolMessage) => {
    closeActions();
    startEditing(message);
  }, [closeActions, startEditing]);

  const copyFromActions = useCallback((message: ProtocolMessage) => {
    closeActions();
    void copy(message.content);
  }, [closeActions, copy]);

  const resolveRequest = useCallback((status: UPeerContact['status'], message: string) => {
    if (!upeerId) return;
    saveContact({ address: '', createdAt: Date.now(), name: alias || upeerId.slice(0, 12), publicKey: null, status, upeerId })
      .then(() => {
        setIsRequest(false);
        setContactStatus(status);
        showSnackbar(message);
      })
      .catch((cause: unknown) => {
        console.warn('No se pudo resolver la solicitud de mensaje.', cause);
        showSnackbar('No se pudo resolver la solicitud');
      });
  }, [alias, showSnackbar, upeerId]);

  const acceptRequest = useCallback(() => resolveRequest('connected', 'Solicitud aceptada'), [resolveRequest]);
  const blockRequest = useCallback(() => resolveRequest('blocked', 'Remitente bloqueado'), [resolveRequest]);

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
      <ChatMessageBubble
        entry={item}
        onLongPress={openActions}
        onToggleReaction={toggleReaction}
        reactions={summarizeReactions(item.message.reactions, selfUpeerId)}
      />
    </View>
  ), [openActions, selfUpeerId, toggleReaction]);

  return (
    <View style={styles.root}>
      <SafeAreaView edges={['top', 'bottom']} style={[styles.root, { backgroundColor: theme.background }]}>
        <ChatHeader alias={alias || 'Conversación'} isRequest={isRequest} onBack={() => router.back()} onOpenInfo={() => router.push({ pathname: '/contact/[upeerId]', params: { upeerId: upeerId ?? '' } })} onUnavailable={showUnavailable} status={contactStatus} typing={peerTyping} upeerId={upeerId ?? ''} />
        <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined} style={styles.root}>
          {isRequest ? <ChatRequestScreen alias={alias || 'Remitente desconocido'} onAccept={acceptRequest} onBlock={blockRequest} receivedAt={requestReceivedAt} /> : <View style={styles.timeline}>
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
          </View>}
          {!!error && (
            <XStack alignItems="center" backgroundColor={theme.backgroundElement} borderTopColor={theme.border} borderTopWidth={StyleSheet.hairlineWidth} gap={7} paddingHorizontal={14} paddingVertical={7}>
              <CircleAlert color={theme.danger} size={15} />
              <Text color={theme.danger} flex={1} fontSize={12}>{error}</Text>
            </XStack>
          )}
          {editing ? (
            <XStack alignItems="center" backgroundColor={theme.backgroundSelected} borderTopColor={theme.border} borderTopWidth={StyleSheet.hairlineWidth} gap={8} paddingHorizontal={14} paddingVertical={7}>
              <Pencil color={theme.accent} size={15} />
              <Text color={theme.text} flex={1} fontSize={12}>Editando mensaje</Text>
              <NativeRipplePressable accessibilityLabel="Cancelar edición" accessibilityRole="button" onPress={cancelEditing} style={styles.cancelEdit}>
                <X color={theme.textSecondary} size={18} />
              </NativeRipplePressable>
            </XStack>
          ) : null}
          {isRequest ? null : <ChatComposer onChange={handleChange} onSend={() => { void submit(); }} onUnavailable={showUnavailable} value={text} />}
        </KeyboardAvoidingView>
      </SafeAreaView>
      {insets.bottom > 0 ? (
        <View pointerEvents="none" style={[styles.nativeBarBackground, { backgroundColor: theme.backgroundElement, height: insets.bottom }]} />
      ) : null}
      <ChatMessageActions
        canEdit={actionMessage?.isMine === true}
        message={actionMessage}
        onClose={closeActions}
        onCopy={copyFromActions}
        onDelete={removeMessage}
        onEdit={editFromActions}
        onToggleReaction={toggleReaction}
        reactions={summarizeReactions(actionMessage?.reactions, selfUpeerId)}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  timeline: { flex: 1, position: 'relative' },
  list: { flex: 1 },
  listContent: { flexGrow: 1, paddingHorizontal: 12, paddingVertical: 16 },
  cancelEdit: { alignItems: 'center', borderRadius: 6, height: 28, justifyContent: 'center', width: 28 },
  nativeBarBackground: { bottom: 0, left: 0, position: 'absolute', right: 0 },
});
