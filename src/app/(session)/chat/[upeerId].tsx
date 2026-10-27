import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { KeyboardAvoidingView, Platform, StyleSheet, View, type FlatList, type ListRenderItemInfo, type NativeScrollEvent, type NativeSyntheticEvent } from 'react-native';
import { router, useLocalSearchParams } from 'expo-router';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';
import { CameraCaptureSheet } from '@/components/chat/camera-capture-sheet';
import { ContactCardSheet } from '@/components/chat/contact-card-sheet';
import { ChatHeader } from '@/components/chat/chat-header';
import { GroupInfoSheet } from '@/components/chat/group-info-sheet';
import { ChatInputArea } from '@/components/chat/chat-input-area';
import { ChatMessageOverlays } from '@/components/chat/chat-message-overlays';
import { ChatOptionsMenu } from '@/components/chat/chat-options-menu';
import { ChatRequestScreen } from '@/components/chat/chat-request-screen';
import { ChatTimelineList } from '@/components/chat/chat-timeline-list';
import { ChatTimelineRow } from '@/components/chat/chat-timeline-row';
import { useSnackbar } from '@/components/snackbar';
import { buildChatTimeline, toInvertedChatTimeline, type ChatTimelineEntry } from '@/domain/chat-timeline';
import { resolveChatBack } from '@/domain/chat-back';
import { useAndroidBack } from '@/hooks/use-android-back';
import { useFirstLoad } from '@/hooks/use-first-load';
import { buildReplyPreview, type ReplyPreview } from '@/domain/chat-reply';
import { decideRequestState } from '@/domain/message-request';
import { useChatComposer } from '@/hooks/use-chat-composer';
import { useAttachmentOpener } from '@/hooks/use-attachment-opener';
import { useAttachmentSender } from '@/hooks/use-attachment-send';
import { useChatMessageSheet } from '@/hooks/use-chat-message-sheet';
import { useRequestResolution } from '@/hooks/use-request-resolution';
import { placeholderName, resolveContactName } from '@/domain/contact-name';
import { useChatSearch } from '@/hooks/use-chat-search';
import { useContactCard } from '@/hooks/use-contact-card';
import { usePresenceLabel } from '@/hooks/use-presence-label';
import { useAndroidKeyboardHeight } from '@/hooks/use-android-keyboard-height';
import { useTheme } from '@/hooks/use-theme';
import { getContacts, saveContact, type UPeerContact } from '@/storage/contact-store';
import type { ProtocolMessage } from '../../../../modules/upeer-transport/src/UPeerTransport.types';
import { getMessages, getConversations, markConversationRead } from '../../../../modules/upeer-transport/src/UPeerTransportModule';

const MESSAGE_LIMIT = 200;
const LATEST_OFFSET = 24;
const SCROLL_BUTTON_OFFSET = 180;

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
  const [peerAvatar, setPeerAvatar] = useState<string | undefined>(undefined);
  const [contactStatus, setContactStatus] = useState<UPeerContact['status']>('offline');
  const [isRequest, setIsRequest] = useState(false);
  const [requestReceivedAt, setRequestReceivedAt] = useState(0);
  const [showScrollButton, setShowScrollButton] = useState(false);
  const [peerTyping, setPeerTyping] = useState(false);
  const [peerPresence, setPeerPresence] = useState<{ lastActive?: number; lastSeen?: number }>({});
  const [contacts, setContacts] = useState<UPeerContact[]>([]);
  const [menuOpen, setMenuOpen] = useState(false);
  const [groupInfoOpen, setGroupInfoOpen] = useState(false);
  const [groupMembers, setGroupMembers] = useState(0);
  const isGroup = upeerId?.startsWith('grp-') === true;
  const { loading: loadingMessages, markLoaded: markMessagesLoaded } = useFirstLoad();

  const refresh = useCallback(async () => {
    if (!upeerId) return;
    const [nextMessages, contacts, conversations] = await Promise.all([getMessages(upeerId, MESSAGE_LIMIT), getContacts(), getConversations()]);
    setMessages(nextMessages);
    markMessagesLoaded();
    setContacts(contacts);
    const contact = contacts.find((item) => item.upeerId === upeerId);
    const ownConversation = conversations.find((conversation) => conversation.upeerId === upeerId);
    const conversationAlias = ownConversation?.alias;
    setPeerTyping(ownConversation?.typing === true);
    setPeerPresence({ lastActive: ownConversation?.lastActive, lastSeen: ownConversation?.lastSeen });
    setPeerAvatar(ownConversation?.avatar);
    setGroupMembers(ownConversation?.members ?? 0);
    const resolvedName = resolveContactName(contact?.name, conversationAlias, upeerId);
    setAlias(resolvedName);
    if (contact && resolvedName !== contact.name && resolvedName !== placeholderName(upeerId)) {
      await saveContact({ ...contact, name: resolvedName });
    }
    const decision = decideRequestState({
      contactStatus: contact?.status,
      hasIncoming: nextMessages.some((message) => !message.isMine),
      hasOutgoing: nextMessages.some((message) => message.isMine),
      isGroup: upeerId.startsWith('grp-'),
    });
    setContactStatus(contact?.status ?? 'offline');
    setIsRequest(decision === 'request');
    setRequestReceivedAt(nextMessages.find((message) => !message.isMine)?.timestamp ?? 0);
    if (decision === 'auto-accept') {
      const base = contact ?? { address: '', createdAt: Date.now(), publicKey: null };
      await saveContact({ ...base, name: resolvedName, status: 'connected', upeerId });
      setContactStatus('connected');
    }
    await markConversationRead(upeerId);
  }, [markMessagesLoaded, upeerId]);

  const {
    cancelContext,
    cancelForwarding,
    dismissPreview,
    editing,
    error,
    forwarding,
    handleChange,
    handleForwarded,
    linkPreview,
    loadingPreview,
    replyingTo,
    setError,
    startEditing,
    startForwarding,
    startReplying,
    submit,
    text,
  } = useChatComposer({ onChanged: refresh, onForwarded: showSnackbar, upeerId });

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
  }, [refresh, setError, upeerId]);

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

  const sheet = useChatMessageSheet({ onChanged: refresh, onEdit: startEditing, onError: setError, onForward: startForwarding, onReply: startReplying, upeerId });
  const { open: openActions, selfUpeerId, toggleReaction } = sheet;

  const replyPreviews = useMemo(() => {
    const byID = new Map(messages.map((message) => [message.id, message]));
    return (message: ProtocolMessage): ReplyPreview | null =>
      buildReplyPreview(message.replyTo ? byID.get(message.replyTo) : undefined, { mine: 'Tú', peer: alias || 'Contacto' });
  }, [alias, messages]);

  const openInfo = useCallback(() => {
    if (isGroup) {
      setGroupInfoOpen(true);
      return;
    }
    router.push({ pathname: '/contact/[upeerId]', params: { upeerId: upeerId ?? '' } });
  }, [isGroup, upeerId]);

  const openContactInfo = useCallback((target: string) => {
    router.push({ pathname: '/contact/[upeerId]', params: { upeerId: target } });
  }, []);
  const reloadContacts = useCallback(() => refresh().catch((cause: unknown) => console.warn('No se pudo recargar la agenda.', cause)), [refresh]);
  const contactCard = useContactCard({ contacts, onOpenContact: openContactInfo, onSaved: reloadContacts });
  const presence = usePresenceLabel(peerPresence.lastActive, peerPresence.lastSeen);
  const androidKeyboard = useAndroidKeyboardHeight();
  const openCard = contactCard.open;
  const savedContactIds = contactCard.savedIds;

  const resolveRequestStatus = useCallback((status: UPeerContact['status']) => {
    setIsRequest(false);
    setContactStatus(status);
  }, []);
  const request = useRequestResolution({ name: alias, onResolved: resolveRequestStatus, upeerId });
  const acceptRequest = request.accept;
  const blockRequest = request.block;

  const showUnavailable = useCallback((label: string) => {
    showSnackbar(`${label}: pendiente en el roadmap`);
  }, [showSnackbar]);

  const openAttachment = useAttachmentOpener();
  const { attach, attachFromCamera, cameraOpen, closeCamera, openLibrary, sendPicked } = useAttachmentSender(upeerId, refresh);

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

  const search = useChatSearch({ messages, onJump: openQuoted });

  useAndroidBack(() => {
    const action = resolveChatBack({
      menuOpen,
      searchActive: search.active,
      sheetOpen: sheet.selected !== null,
      cardOpen: contactCard.selected !== null,
      groupOpen: groupInfoOpen,
      forwarding: forwarding !== null,
      composing: editing !== null || replyingTo !== null,
    });
    if (action === 'close-menu') { setMenuOpen(false); return true; }
    if (action === 'close-search') { search.close(); return true; }
    if (action === 'close-sheet') { sheet.close(); return true; }
    if (action === 'close-card') { contactCard.close(); return true; }
    if (action === 'close-group') { setGroupInfoOpen(false); return true; }
    if (action === 'cancel-forwarding') { cancelForwarding(); return true; }
    if (action === 'cancel-composing') { cancelContext(); return true; }
    return false;
  });
  const { highlightedID } = search;

  const renderItem = useCallback(({ item }: ListRenderItemInfo<ChatTimelineEntry>) => (
    <ChatTimelineRow
      entry={item}
      highlighted={item.message.id === highlightedID}
      onLongPress={openActions}
      onOpenAttachment={openAttachment}
      onOpenCard={openCard}
      onOpenReply={openQuoted}
      onToggleReaction={toggleReaction}
      onUnavailable={showUnavailable}
      reply={replyPreviews(item.message)}
      savedContacts={savedContactIds}
      selfUpeerId={selfUpeerId}
    />
  ), [highlightedID, openActions, openAttachment, openCard, openQuoted, replyPreviews, savedContactIds, selfUpeerId, showUnavailable, toggleReaction]);

  return (
    <View style={styles.root}>
      <SafeAreaView edges={['top', 'bottom']} style={[styles.root, { backgroundColor: theme.background }]}>
        <ChatHeader alias={alias || 'Conversación'} avatar={peerAvatar} isRequest={isRequest} onBack={() => router.back()} onOpenInfo={openInfo} onOpenMenu={() => setMenuOpen(true)} onUnavailable={showUnavailable} presence={isGroup ? (groupMembers > 0 ? `${groupMembers} miembros` : 'Grupo') : presence} status={contactStatus} typing={peerTyping} upeerId={upeerId ?? ''} />
        <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined} style={[styles.root, androidKeyboard > 0 ? { paddingBottom: androidKeyboard } : null]}>
          {isRequest ? <ChatRequestScreen alias={alias || 'Remitente desconocido'} onAccept={acceptRequest} onBlock={blockRequest} receivedAt={requestReceivedAt} /> : (
            <ChatTimelineList
              data={timeline}
              listRef={listRef}
              loading={loadingMessages}
              onScroll={handleScroll}
              onScrollToLatest={scrollToLatest}
              renderItem={renderItem}
              showScrollButton={showScrollButton}
            />
          )}
          <ChatInputArea
            composer={{
              editing: editing !== null,
              error,
              hidden: isRequest,
              loadingPreview,
              onChange: handleChange,
              onAttach: attach,
              onCamera: attachFromCamera,
              onCancelContext: cancelContext,
              onDismissPreview: dismissPreview,
              onSend: () => { void submit(); },
              onUnavailable: showUnavailable,
              preview: linkPreview,
              reply: buildReplyPreview(replyingTo ?? undefined, { mine: 'Tú', peer: alias || 'Contacto' }),
              value: text,
            }}
            search={search}
          />
        </KeyboardAvoidingView>
      </SafeAreaView>
      {insets.bottom > 0 ? (
        <View pointerEvents="none" style={[styles.nativeBarBackground, { backgroundColor: theme.background, height: insets.bottom }]} />
      ) : null}
      <CameraCaptureSheet onClose={closeCamera} onOpenLibrary={openLibrary} onPick={(attachment) => { void sendPicked(attachment); }} visible={cameraOpen} />
      <ContactCardSheet action={contactCard.action} card={contactCard.selected} error={contactCard.error} onClose={contactCard.close} visible={contactCard.selected !== null} />
      <GroupInfoSheet groupId={isGroup && groupInfoOpen ? upeerId ?? null : null} onClose={() => setGroupInfoOpen(false)} />
      <ChatOptionsMenu isGroup={isGroup} messages={messages} name={alias} onChanged={refresh} onCloseChat={() => router.back()} onCloseMenu={() => setMenuOpen(false)} onOpenInfo={openInfo} onSearch={search.open} upeerId={upeerId ?? ''} visible={menuOpen} />
      <ChatMessageOverlays contacts={contacts} forwarding={forwarding} onForwardClose={cancelForwarding} onForwarded={handleForwarded} selfUpeerId={selfUpeerId} sheet={sheet} />
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  nativeBarBackground: { bottom: 0, left: 0, position: 'absolute', right: 0 },
});
