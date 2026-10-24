import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { KeyboardAvoidingView, Platform, StyleSheet, View, type FlatList, type ListRenderItemInfo, type NativeScrollEvent, type NativeSyntheticEvent } from 'react-native';
import { router, useLocalSearchParams } from 'expo-router';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';
import { ChatComposerArea } from '@/components/chat/chat-composer-area';
import { ContactCardSheet } from '@/components/chat/contact-card-sheet';
import { ChatDateSeparator } from '@/components/chat/chat-date-separator';
import { ChatHeader } from '@/components/chat/chat-header';
import { ChatMessageOverlays } from '@/components/chat/chat-message-overlays';
import { ChatMessageBubble } from '@/components/chat/chat-message-bubble';
import { ChatRequestScreen } from '@/components/chat/chat-request-screen';
import { ChatTimelineList } from '@/components/chat/chat-timeline-list';
import { useSnackbar } from '@/components/snackbar';
import { buildChatTimeline, toInvertedChatTimeline, type ChatTimelineEntry } from '@/domain/chat-timeline';
import { summarizeReactions } from '@/domain/chat-reactions';
import { buildReplyPreview, type ReplyPreview } from '@/domain/chat-reply';
import { forwardResultMessage } from '@/domain/forward';
import { decideRequestState } from '@/domain/message-request';
import { useChatMessageSheet } from '@/hooks/use-chat-message-sheet';
import { useContactCard } from '@/hooks/use-contact-card';
import { usePresenceLabel } from '@/hooks/use-presence-label';
import { useTheme } from '@/hooks/use-theme';
import { getContacts, saveContact, type UPeerContact } from '@/storage/contact-store';
import type { ProtocolMessage } from '../../../../modules/upeer-transport/src/UPeerTransport.types';
import { editChat, getMessages, getConversations, markConversationRead, sendChat, sendTyping } from '../../../../modules/upeer-transport/src/UPeerTransportModule';

const MESSAGE_LIMIT = 200;
const LATEST_OFFSET = 24;
const SCROLL_BUTTON_OFFSET = 180;
const TYPING_INTERVAL_MS = 3000;

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
  const [replyingTo, setReplyingTo] = useState<ProtocolMessage | null>(null);
  const [forwarding, setForwarding] = useState<ProtocolMessage | null>(null);
  const [error, setError] = useState('');
  const [showScrollButton, setShowScrollButton] = useState(false);
  const [peerTyping, setPeerTyping] = useState(false);
  const [peerLastSeen, setPeerLastSeen] = useState<number | undefined>(undefined);
  const [contacts, setContacts] = useState<UPeerContact[]>([]);
  const typingSentAtRef = useRef(0);


  const refresh = useCallback(async () => {
    if (!upeerId) return;
    const [nextMessages, contacts, conversations] = await Promise.all([getMessages(upeerId, MESSAGE_LIMIT), getContacts(), getConversations()]);
    setMessages(nextMessages);
    setContacts(contacts);
    const contact = contacts.find((item) => item.upeerId === upeerId);
    const ownConversation = conversations.find((conversation) => conversation.upeerId === upeerId);
    const conversationAlias = ownConversation?.alias;
    setPeerTyping(ownConversation?.typing === true);
    setPeerLastSeen(ownConversation?.lastSeen);
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
    const currentReply = replyingTo;
    setText('');
    setEditing(null);
    setReplyingTo(null);
    setError('');
    try {
      if (currentEdit) {
        await editChat(upeerId, currentEdit.id, content);
      } else {
        await sendChat(upeerId, content, currentReply?.id ?? '');
      }
      await refresh();
    } catch (cause) {
      setText(content);
      setEditing(currentEdit);
      setReplyingTo(currentReply);
      setError(cause instanceof Error ? cause.message : 'No se pudo enviar el mensaje.');
    }
  };

  const startEditing = useCallback((message: ProtocolMessage) => {
    setReplyingTo(null);
    setEditing(message);
    setText(message.content);
    setError('');
  }, []);

  const startReplying = useCallback((message: ProtocolMessage) => {
    setEditing(null);
    setReplyingTo(message);
    setError('');
  }, []);

  const cancelContext = useCallback(() => {
    if (editing) setText('');
    setEditing(null);
    setReplyingTo(null);
  }, [editing]);

  const startForwarding = useCallback((message: ProtocolMessage) => {
    setForwarding(message);
    setError('');
  }, []);

  const handleForwarded = useCallback((count: number, failed: number) => {
    setForwarding(null);
    showSnackbar(forwardResultMessage(count, failed));
  }, [showSnackbar]);

  const sheet = useChatMessageSheet({ onChanged: refresh, onEdit: startEditing, onError: setError, onForward: startForwarding, onReply: startReplying, upeerId });
  const { open: openActions, selfUpeerId, toggleReaction } = sheet;

  const replyPreviews = useMemo(() => {
    const byID = new Map(messages.map((message) => [message.id, message]));
    return (message: ProtocolMessage): ReplyPreview | null =>
      buildReplyPreview(message.replyTo ? byID.get(message.replyTo) : undefined, { mine: 'Tú', peer: alias || 'Contacto' });
  }, [alias, messages]);

  const openContactInfo = useCallback((target: string) => {
    router.push({ pathname: '/contact/[upeerId]', params: { upeerId: target } });
  }, []);
  const reloadContacts = useCallback(() => refresh().catch((cause: unknown) => console.warn('No se pudo recargar la agenda.', cause)), [refresh]);
  const contactCard = useContactCard({ contacts, onOpenContact: openContactInfo, onSaved: reloadContacts });
  const presence = usePresenceLabel(peerLastSeen);
  const openCard = contactCard.open;
  const savedContactIds = contactCard.savedIds;

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

  const openQuoted = useCallback((messageID: string) => {
    const index = timeline.findIndex((entry) => entry.message.id === messageID);
    if (index < 0) {
      showSnackbar('El mensaje citado ya no está en el hilo');
      return;
    }
    atLatestRef.current = index === 0;
    listRef.current?.scrollToIndex({ animated: true, index, viewPosition: 0.5 });
  }, [showSnackbar, timeline]);

  const renderItem = useCallback(({ item }: ListRenderItemInfo<ChatTimelineEntry>) => (
    <View>
      {item.showDateSeparator ? <ChatDateSeparator timestamp={item.message.timestamp} /> : null}
      <ChatMessageBubble
        entry={item}
        onLongPress={openActions}
        onOpenCard={openCard}
        onOpenReply={openQuoted}
        onToggleReaction={toggleReaction}
        reactions={summarizeReactions(item.message.reactions, selfUpeerId)}
        reply={replyPreviews(item.message)}
        savedContacts={savedContactIds}
      />
    </View>
  ), [openActions, openCard, openQuoted, replyPreviews, savedContactIds, selfUpeerId, toggleReaction]);

  return (
    <View style={styles.root}>
      <SafeAreaView edges={['top', 'bottom']} style={[styles.root, { backgroundColor: theme.background }]}>
        <ChatHeader alias={alias || 'Conversación'} isRequest={isRequest} onBack={() => router.back()} onOpenInfo={() => router.push({ pathname: '/contact/[upeerId]', params: { upeerId: upeerId ?? '' } })} onUnavailable={showUnavailable} presence={presence} status={contactStatus} typing={peerTyping} upeerId={upeerId ?? ''} />
        <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined} style={styles.root}>
          {isRequest ? <ChatRequestScreen alias={alias || 'Remitente desconocido'} onAccept={acceptRequest} onBlock={blockRequest} receivedAt={requestReceivedAt} /> : (
            <ChatTimelineList
              data={timeline}
              listRef={listRef}
              onScroll={handleScroll}
              onScrollToLatest={scrollToLatest}
              renderItem={renderItem}
              showScrollButton={showScrollButton}
            />
          )}
          <ChatComposerArea
            editing={editing !== null}
            error={error}
            hidden={isRequest}
            onChange={handleChange}
            onCancelContext={cancelContext}
            onSend={() => { void submit(); }}
            onUnavailable={showUnavailable}
            reply={buildReplyPreview(replyingTo ?? undefined, { mine: 'Tú', peer: alias || 'Contacto' })}
            value={text}
          />
        </KeyboardAvoidingView>
      </SafeAreaView>
      {insets.bottom > 0 ? (
        <View pointerEvents="none" style={[styles.nativeBarBackground, { backgroundColor: theme.backgroundElement, height: insets.bottom }]} />
      ) : null}
      <ContactCardSheet action={contactCard.action} card={contactCard.selected} error={contactCard.error} onClose={contactCard.close} visible={contactCard.selected !== null} />
      <ChatMessageOverlays contacts={contacts} forwarding={forwarding} onForwardClose={() => setForwarding(null)} onForwarded={handleForwarded} selfUpeerId={selfUpeerId} sheet={sheet} />
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  nativeBarBackground: { bottom: 0, left: 0, position: 'absolute', right: 0 },
});
