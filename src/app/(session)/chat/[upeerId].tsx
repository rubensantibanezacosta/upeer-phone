import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { KeyboardAvoidingView, Platform, StyleSheet, View, type FlatList } from 'react-native';
import { router, useLocalSearchParams } from 'expo-router';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';
import { CameraCaptureSheet } from '@/components/chat/camera-capture-sheet';
import { ContactCardSheet } from '@/components/chat/contact-card-sheet';
import { ChatHeader } from '@/components/chat/chat-header';
import { ChatInputArea } from '@/components/chat/chat-input-area';
import { ChatMessageOverlays } from '@/components/chat/chat-message-overlays';
import { ChatOptionsMenu } from '@/components/chat/chat-options-menu';
import { ChatRequestScreen } from '@/components/chat/chat-request-screen';
import { ChatSelectionBar, ChatSelectionToolbar } from '@/components/chat/chat-selection-bars';
import { ChatTimelineList } from '@/components/chat/chat-timeline-list';
import { MediaViewer } from '@/components/media/media-viewer';
import { useSnackbar } from '@/components/snackbar';
import { buildChatTimeline, mergeChatHistory, toInvertedChatTimeline, type ChatTimelineEntry } from '@/domain/chat-timeline';
import { dropExpired } from '@/domain/retention';
import type { AttachmentInfo } from '@/domain/attachment';
import type { ConversationActivity } from '@/domain/chat-preview';
import { useChatBack } from '@/hooks/use-chat-back';
import { useChatAnchor } from '@/hooks/use-chat-anchor';
import { useChatTimelineScroll } from '@/hooks/use-chat-timeline-scroll';
import { useFirstLoad } from '@/hooks/use-first-load';
import { buildReplyPreview, type ReplyPreview } from '@/domain/chat-reply';
import { decideRequestState } from '@/domain/message-request';
import { useChatComposer } from '@/hooks/use-chat-composer';
import { useSelfUpeerId } from '@/hooks/use-self-upeer-id';
import { useAttachmentOpener } from '@/hooks/use-attachment-opener';
import { useAttachmentRequester } from '@/hooks/use-attachment-request';
import { useAttachmentSender } from '@/hooks/use-attachment-send';
import { useChatMessageSheet } from '@/hooks/use-chat-message-sheet';
import { useRequestResolution } from '@/hooks/use-request-resolution';
import { placeholderName, resolveContactName } from '@/domain/contact-name';
import { isSelfConversation } from '@/domain/self-conversation';
import { changedSections, hasUnreadIncoming, snapshotRefresh, type ChatRefreshSnapshot } from '@/domain/chat-refresh';
import { useChatRowRenderer } from '@/hooks/use-chat-row-renderer';
import { useChatComposerArea } from '@/hooks/use-chat-composer-area';
import { useChatSearchController } from '@/hooks/use-chat-search-controller';
import { useChatRetention } from '@/hooks/use-chat-retention';
import { useChatStyle } from '@/hooks/use-chat-style';
import { useChatSelection } from '@/hooks/use-chat-selection';
import { useContactCard } from '@/hooks/use-contact-card';
import { usePresenceLabel } from '@/hooks/use-presence-label';
import { useAndroidKeyboardHeight } from '@/hooks/use-android-keyboard-height';
import { useTheme } from '@/hooks/use-theme';
import type { PickedAttachment } from '@/services/attachment-picker';
import { getContacts, saveContact, type UPeerContact } from '@/storage/contact-store';
import type { ProtocolMessage } from '../../../../modules/upeer-transport/src/UPeerTransport.types';
import { getMessages, getConversations, markConversationRead, startCall as startChatCall } from '../../../../modules/upeer-transport/src/UPeerTransportModule';

const MESSAGE_LIMIT = 200;

export default function ChatRoute() {
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  const { showSnackbar } = useSnackbar();
  const { messageId, upeerId } = useLocalSearchParams<{ messageId?: string; upeerId: string }>();
  const listRef = useRef<FlatList<ChatTimelineEntry>>(null);
  const [messages, setMessages] = useState<ProtocolMessage[]>([]);
  const [alias, setAlias] = useState('');
  const [peerAvatar, setPeerAvatar] = useState<string | undefined>(undefined);
  const [contactStatus, setContactStatus] = useState<UPeerContact['status']>('offline');
  const [isRequest, setIsRequest] = useState(false);
  const [requestReceivedAt, setRequestReceivedAt] = useState(0);
  const [peerActivity, setPeerActivity] = useState<ConversationActivity | ''>('');
  const [peerPresence, setPeerPresence] = useState<number | undefined>(undefined);
  const [contacts, setContacts] = useState<UPeerContact[]>([]);
  const [menuOpen, setMenuOpen] = useState(false);
  const [groupMembers, setGroupMembers] = useState(0);
  const isGroup = upeerId?.startsWith('grp-') === true;
  const { loading: loadingMessages, markLoaded: markMessagesLoaded } = useFirstLoad();
  const refreshSnapshot = useRef<ChatRefreshSnapshot | null>(null);

  const refresh = useCallback(async () => {
    if (!upeerId) return;
    const [nextMessages, nextContacts, conversations] = await Promise.all([getMessages(upeerId, MESSAGE_LIMIT), getContacts(), getConversations()]);
    const snapshot = snapshotRefresh({ messages: nextMessages, contacts: nextContacts, conversations });
    const change = changedSections(refreshSnapshot.current, snapshot);
    refreshSnapshot.current = snapshot;
    if (change.messages) {
      setMessages((current) => dropExpired(mergeChatHistory(nextMessages, current), Date.now()));
    }
    markMessagesLoaded();
    if (change.contacts) setContacts(nextContacts);
    const contact = nextContacts.find((item) => item.upeerId === upeerId);
    const ownConversation = conversations.find((conversation) => conversation.upeerId === upeerId);
    const conversationAlias = ownConversation?.alias;
    setPeerActivity(ownConversation?.activity ?? '');
    setPeerPresence(ownConversation?.lastActive);
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
    if (hasUnreadIncoming(nextMessages)) await markConversationRead(upeerId);
  }, [markMessagesLoaded, upeerId]);

  const composer = useChatComposer({ onChanged: refresh, onForwarded: showSnackbar, upeerId });
  const {
    cancelContext,
    cancelForwarding,
    editing,
    forwarding,
    handleForwarded,
    replyingTo,
    setError,
    startEditing,
    startForwarding,
    startReplying,
  } = composer;

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

  const missingQuoted = useCallback(() => showSnackbar('El mensaje citado ya no está en el hilo'), [showSnackbar]);
  const { handleScroll, scrollToLatest, scrollToMessage, showScrollButton } = useChatTimelineScroll({
    listRef,
    messageCount: messages.length,
    onMissingQuoted: missingQuoted,
    timeline,
  });
  const clearMessageId = useCallback(() => router.setParams({ messageId: undefined }), []);
  const { handleScrollToIndexFailed, highlightedMessageId } = useChatAnchor({
    loaded: !loadingMessages,
    listRef,
    messageId,
    onConsumed: clearMessageId,
    timeline,
  });

  const forwardOne = useCallback((message: ProtocolMessage) => startForwarding([message]), [startForwarding]);
  const sheet = useChatMessageSheet({ onChanged: refresh, onEdit: startEditing, onError: setError, onForward: forwardOne, onReply: startReplying, upeerId });
  const { open: openActions, selfUpeerId, toggleReaction } = sheet;
  const ownUpeerId = useSelfUpeerId();
  const isSelfChat = !isGroup && isSelfConversation(upeerId, ownUpeerId);
  const selection = useChatSelection({ messages, onChanged: refresh, onError: setError, onForward: startForwarding, upeerId });

  const replyPreviews = useMemo(() => {
    const byID = new Map(messages.map((message) => [message.id, message]));
    return (message: ProtocolMessage): ReplyPreview | null =>
      buildReplyPreview(message.replyTo ? byID.get(message.replyTo) : undefined, { mine: 'Tú', peer: alias || 'Contacto' });
  }, [alias, messages]);

  const openInfo = useCallback(() => {
    if (isGroup) {
      router.push({ pathname: '/group/[groupId]', params: { groupId: upeerId ?? '' } });
      return;
    }
    router.push({ pathname: '/contact/[upeerId]', params: { upeerId: upeerId ?? '' } });
  }, [isGroup, upeerId]);

  const openContactInfo = useCallback((target: string) => {
    router.push({ pathname: '/contact/[upeerId]', params: { upeerId: target } });
  }, []);
  const reloadContacts = useCallback(() => refresh().catch((cause: unknown) => console.warn('No se pudo recargar la agenda.', cause)), [refresh]);
  const contactCard = useContactCard({ contacts, onOpenContact: openContactInfo, onSaved: reloadContacts });
  const presence = usePresenceLabel(peerPresence);
  const { notice: retentionNotice } = useChatRetention(upeerId);
  const { styleId: chatStyleId } = useChatStyle(upeerId);
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

  const startCall = useCallback((kind: 'audio' | 'video') => {
    if (isSelfChat) {
      showSnackbar('No puedes llamarte a ti mismo');
      return;
    }
    if (isGroup) {
      showSnackbar('La llamada de grupo llegará más adelante');
      return;
    }
    void startChatCall(upeerId ?? '', kind).then((callId) => {
      if (!callId) showSnackbar('No se pudo iniciar la llamada');
    }).catch((cause: unknown) => {
      console.warn('No se pudo iniciar la llamada.', cause);
      showSnackbar('No se pudo iniciar la llamada');
    });
  }, [isGroup, isSelfChat, showSnackbar, upeerId]);

  const [media, setMedia] = useState<AttachmentInfo | null>(null);
  const requestAttachment = useAttachmentRequester(upeerId ?? '');
  const openAttachment = useAttachmentOpener(setMedia, requestAttachment);
  const { attach, attachFromCamera, cameraOpen, closeCamera, openLibrary, sendPicked, sendVoiceNote } = useAttachmentSender(upeerId, refresh);
  const onVoiceNote = useCallback((attachment: PickedAttachment) => { void sendVoiceNote(attachment); }, [sendVoiceNote]);
  const composerArea = useChatComposerArea({ alias, attach, attachFromCamera, composer, hidden: isRequest, onVoiceError: showSnackbar, onVoiceNote });

  const search = useChatSearchController({
    chatId: upeerId ?? '',
    loaded: !loadingMessages,
    messages,
    onJump: scrollToMessage,
    onMerge: useCallback((page: readonly ProtocolMessage[]) => setMessages((current) => mergeChatHistory(current, page)), []),
    onMissing: useCallback(() => showSnackbar('Ese mensaje ya no está en la conversación'), [showSnackbar]),
  });

  useChatBack(
    {
      cardOpen: contactCard.selected !== null,
      composing: editing !== null || replyingTo !== null,
      forwarding: forwarding !== null,
      menuOpen,
      searchActive: search.active,
      selecting: selection.active,
      sheetOpen: sheet.selected !== null,
    },
    {
      cancelComposing: cancelContext,
      cancelForwarding,
      closeCard: contactCard.close,
      closeMenu: () => setMenuOpen(false),
      closeSearch: search.close,
      closeSheet: sheet.close,
      exitSelection: selection.clear,
    },
  );
  const { highlightedID } = search;

  const renderItem = useChatRowRenderer({
    highlightedID,
    highlightedMessageId,
    onLongPress: openActions,
    onOpenAttachment: openAttachment,
    onOpenCard: openCard,
    onScrollToMessage: scrollToMessage,
    onToggleReaction: toggleReaction,
    onToggleSelect: selection.toggle,
    replyPreviews,
    savedContactIds,
    selectable: selection.active,
    selected: selection.selected,
    selfUpeerId,
  });

  return (
    <View style={styles.root}>
      <SafeAreaView edges={['top', 'bottom']} style={[styles.root, { backgroundColor: theme.background }]}>
        {selection.active ? <ChatSelectionBar count={selection.chosen.length} onClose={selection.clear} /> : null}
        <ChatHeader alias={alias || 'Conversación'} avatar={peerAvatar} isRequest={isRequest} isSelf={isSelfChat} onBack={() => router.back()} onOpenInfo={openInfo} onOpenMenu={() => setMenuOpen(true)} onStartCall={startCall} onUnavailable={showUnavailable} presence={isGroup ? (groupMembers > 0 ? `${groupMembers} miembros` : 'Grupo') : presence} status={contactStatus} activity={peerActivity} upeerId={upeerId ?? ''} />
        <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined} style={[styles.root, androidKeyboard > 0 ? { paddingBottom: androidKeyboard } : null]}>
          {isRequest ? <ChatRequestScreen alias={alias || 'Remitente desconocido'} onAccept={acceptRequest} onBlock={blockRequest} receivedAt={requestReceivedAt} upeerId={upeerId ?? ''} /> : (
            <ChatTimelineList
              activity={peerActivity}
              data={timeline}
              listRef={listRef}
              loading={loadingMessages}
              notice={retentionNotice}
              onScroll={handleScroll}
              onScrollToIndexFailed={handleScrollToIndexFailed}
              onScrollToLatest={scrollToLatest}
              renderItem={renderItem}
              showScrollButton={showScrollButton}
              styleId={chatStyleId}
            />
          )}
          {selection.active ? (
            <ChatSelectionToolbar selection={selection} />
          ) : <ChatInputArea composer={composerArea} search={search} />}
        </KeyboardAvoidingView>
      </SafeAreaView>
      {insets.bottom > 0 ? (
        <View pointerEvents="none" style={[styles.nativeBarBackground, { backgroundColor: theme.background, height: insets.bottom }]} />
      ) : null}
      <CameraCaptureSheet onClose={closeCamera} onOpenLibrary={openLibrary} onPick={(attachment) => { void sendPicked(attachment); }} visible={cameraOpen} />
      <ContactCardSheet action={contactCard.action} card={contactCard.selected} error={contactCard.error} onClose={contactCard.close} visible={contactCard.selected !== null} />
      <ChatOptionsMenu isGroup={isGroup} isSelf={isSelfChat} messages={messages} name={alias} onChanged={refresh} onCloseChat={() => router.back()} onCloseMenu={() => setMenuOpen(false)} onOpenInfo={openInfo} onOpenMessage={scrollToMessage} onSearch={search.open} upeerId={upeerId ?? ''} visible={menuOpen} />
      <ChatMessageOverlays contacts={contacts} forwarding={forwarding} onForwardClose={cancelForwarding} onForwarded={handleForwarded} onSelect={(message) => { sheet.close(); selection.start(message); }} selfUpeerId={selfUpeerId} sheet={sheet} />
      <MediaViewer attachment={media} onClose={() => setMedia(null)} />
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  nativeBarBackground: { bottom: 0, left: 0, position: 'absolute', right: 0 },
});
