import { useEffect, useMemo, useState } from 'react';
import { ScrollView, StyleSheet, View } from 'react-native';
import { router } from 'expo-router';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ellipsis, MessageCircle, MessageSquarePlus } from '@tamagui/lucide-icons-2';
import { H1, Paragraph, SizableText, Text, XStack, YStack } from 'tamagui';

import { ChatContextMenu } from '@/components/chat-context-menu';
import { ChatListMenu } from '@/components/chat-list-menu';
import { BottomSheet } from '@/components/bottom-sheet';
import { LoadingState } from '@/components/loading-state';
import { useFirstLoad } from '@/hooks/use-first-load';
import { NewConversationList } from '@/components/new-conversation-list';
import { ChatListItem, type ChatListEntry } from '@/components/chat-list-item';
import { CHAT_LIST_FILTERS, filterChatEntries, isChatFilterActive, type ChatListFilter } from '@/domain/chat-list-filters';
import { previewMessage } from '@/domain/message-preview';
import { flagMessage, flagsFor, orderByPinned, type ConversationFlag } from '@/domain/conversation-flags';
import { useConversationFlags } from '@/hooks/use-conversation-flags';
import { useNow } from '@/hooks/use-now';
import { setConversationFlag } from '@/storage/conversation-flags-store';
import { classifyConversations } from '@/domain/incoming-requests';
import { useSnackbar } from '@/components/snackbar';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';
import type { UPeerContact } from '@/storage/contact-store';
import { getContacts, setContactFavorite } from '@/storage/contact-store';
import type { ProtocolConversation } from '../../../../modules/upeer-transport/src/UPeerTransport.types';
import { SearchField } from '@/components/search-field';
import { getConversations, markConversationRead } from '../../../../modules/upeer-transport/src/UPeerTransportModule';
import { unreadConversationIds } from '@/domain/chat-unread';
import { useSession } from '@/components/session-shell';
import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { NativeRippleButton } from '@/components/native-ripple-button';
import { SyncProgressBanner } from '@/components/sync-progress-banner';

const contactStatusLabels: Record<UPeerContact['status'], string> = {
  pending: 'Pendiente',
  incoming: 'Solicitud de contacto recibida',
  connected: 'Contacto conectado',
  offline: 'Desconectado',
  blocked: 'Bloqueado',
};

export default function ChatsRoute() {
  const theme = useTheme();
  const scale = useTextScale();
  const { showSnackbar } = useSnackbar();
  const conversationFlags = useConversationFlags();
  const { signOut } = useSession();
  const now = useNow();
  const [contacts, setContacts] = useState<UPeerContact[]>([]);
  const [conversations, setConversations] = useState<ProtocolConversation[]>([]);
  const [selectedEntry, setSelectedEntry] = useState<ChatListEntry | null>(null);
  const [menuOpen, setMenuOpen] = useState(false);
  const [composerOpen, setComposerOpen] = useState(false);
  const [filter, setFilter] = useState<ChatListFilter>('all');
  const [query, setQuery] = useState('');
  const { loading, markLoaded } = useFirstLoad();

  useEffect(() => {
    getContacts().then(setContacts).catch((cause: unknown) => console.warn('No se pudieron cargar los contactos para Chats.', cause));
    const loadConversations = () => {
      getConversations()
        .then((next) => {
          setConversations(next);
          markLoaded();
        })
        .catch((cause: unknown) => console.warn('No se pudieron cargar las conversaciones nativas.', cause));
    };
    loadConversations();
    const timer = setInterval(loadConversations, 2000);
    return () => clearInterval(timer);
  }, [markLoaded]);

  const partition = useMemo(() => classifyConversations(conversations, contacts), [contacts, conversations]);
  const requestIds = useMemo(() => new Set(partition.requests.map((request) => request.upeerId)), [partition.requests]);

  const entries: ChatListEntry[] = useMemo(() => (conversations.length > 0
    ? conversations
      .filter((conversation) => !partition.hidden.some((hidden) => hidden.upeerId === conversation.upeerId))
      .map((conversation) => ({
        upeerId: conversation.upeerId,
        title: conversation.alias || conversation.upeerId.slice(0, 12),
        avatar: conversation.avatar,
        preview: previewMessage(conversation.lastMessage) || 'Sin mensajes',
        timestamp: conversation.lastTimestamp,
        unread: conversation.unread,
        isMine: conversation.lastIsMine === true,
        lastStatus: conversation.lastStatus ?? null,
        incoming: requestIds.has(conversation.upeerId),
        favorite: contacts.some((contact) => contact.upeerId === conversation.upeerId && contact.isFavorite === true),
        typing: conversation.typing === true,
        lastSeen: conversation.lastActive || conversation.lastSeen,
        group: conversation.group === true,
        members: conversation.members,
        ...flagsFor(conversationFlags, conversation.upeerId),
      }))
    : contacts.filter((contact) => contact.status !== 'blocked').map((contact) => ({
      upeerId: contact.upeerId,
      title: contact.name,
      favorite: contact.isFavorite === true,
      preview: contactStatusLabels[contact.status],
      timestamp: 0,
      unread: 0,
      isMine: false,
      lastStatus: null,
      pending: contact.status === 'pending',
      incoming: contact.status === 'incoming',
      ...flagsFor(conversationFlags, contact.upeerId),
    }))), [contacts, conversationFlags, conversations, partition.hidden, requestIds]);

  const visibleEntries = useMemo(() => orderByPinned(filterChatEntries(entries, filter, query), conversationFlags), [conversationFlags, entries, filter, query]);
  const filtering = isChatFilterActive(filter, query);

  const openChat = (entry: ChatListEntry) => {
    setSelectedEntry(null);
    router.push({ pathname: '/chat/[upeerId]', params: { upeerId: entry.upeerId } });
  };

  const openContextMenu = (entry: ChatListEntry) => setSelectedEntry(entry);

  const showUnavailableAction = (label: string) => {
    setSelectedEntry(null);
    showSnackbar(`${label} estará disponible próximamente`);
  };

  const toggleEntryFlag = (entry: ChatListEntry, flag: ConversationFlag) => {
    setSelectedEntry(null);
    setConversationFlag(entry.upeerId, flag)
      .then(() => showSnackbar(flagMessage(flag, !entry[flag])))
      .catch((cause: unknown) => {
        console.warn('No se pudo cambiar el ajuste de la conversación.', cause);
        showSnackbar('No se pudo cambiar el ajuste de la conversación');
      });
  };

  const toggleEntryFavorite = (entry: ChatListEntry) => {
    setSelectedEntry(null);
    setContactFavorite(entry.upeerId, entry.favorite !== true)
      .then(() => getContacts())
      .then((list) => {
        setContacts(list);
        showSnackbar(entry.favorite ? 'Quitado de Favoritos' : 'Añadido a Favoritos');
      })
      .catch((cause: unknown) => {
        console.warn('No se pudo cambiar el favorito de la conversación.', cause);
        showSnackbar('No se pudo cambiar el favorito');
      });
  };

  const markEntryRead = (entry: ChatListEntry) => {
    setSelectedEntry(null);
    markConversationRead(entry.upeerId)
      .then(getConversations)
      .then(setConversations)
      .catch((cause: unknown) => {
        console.warn('No se pudo marcar la conversación como leída.', cause);
        showSnackbar('No se pudo marcar la conversación como leída');
      });
  };

  const markAllRead = () => {
    const targets = unreadConversationIds(conversations);
    if (targets.length === 0) {
      showSnackbar('No hay conversaciones sin leer');
      return;
    }
    Promise.all(targets.map((upeerId) => markConversationRead(upeerId)))
      .then(getConversations)
      .then((next) => {
        setConversations(next);
        showSnackbar('Todo marcado como leído');
      })
      .catch((cause: unknown) => {
        console.warn('No se pudieron marcar las conversaciones como leídas.', cause);
        showSnackbar('No se pudieron marcar como leídas');
      });
  };

  return (
    <SafeAreaView edges={['top']} style={[styles.root, { backgroundColor: theme.background }]}>
      <ScrollView contentContainerStyle={styles.chatContent}>
        <XStack alignItems="center" justifyContent="space-between">
          <H1 color={theme.text} fontSize={scale(24)} fontWeight="700">uPeer</H1>
          <XStack alignItems="center" gap={4}>
            <NativeRipplePressable accessibilityLabel="Nueva conversación" accessibilityRole="button" onPress={() => setComposerOpen(true)} style={styles.chatMenuButton}><MessageSquarePlus color={theme.text} size={21} /></NativeRipplePressable>
            <NativeRipplePressable accessibilityLabel="Más opciones" accessibilityRole="button" onPress={() => setMenuOpen((value) => !value)} style={styles.chatMenuButton}><Ellipsis color={theme.text} size={22} /></NativeRipplePressable>
          </XStack>
        </XStack>
        <SyncProgressBanner />
        <SearchField onChangeText={setQuery} placeholder="Buscar un chat o iniciar uno nuevo" value={query} />
        <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.chatFiltersScroll} contentContainerStyle={styles.chatFilters}>
          {CHAT_LIST_FILTERS.map((item) => {
            const active = filter === item.id;
            return <NativeRipplePressable key={item.id} onPress={() => setFilter(item.id)} style={[styles.chatFilterChip, { backgroundColor: active ? theme.accent : theme.backgroundElement }]}><Text color={active ? theme.onAccent : theme.text} fontSize={scale(13)} fontWeight={active ? '700' : '400'}>{item.label}</Text></NativeRipplePressable>;
          })}
        </ScrollView>
        {visibleEntries.length > 0 ? (
          <YStack marginTop={2}>
            {visibleEntries.map((entry) => <ChatListItem entry={entry} key={entry.upeerId} now={now} onOpen={openChat} onOpenMenu={openContextMenu} />)}
          </YStack>
        ) : loading ? (
          <LoadingState compact label="Cargando conversaciones…" />
        ) : (
          <YStack alignItems="center" flex={1} gap={12} justifyContent="center" padding={24}>
            <View style={[styles.emptyAvatar, { backgroundColor: theme.backgroundElement }]}>
              <MessageCircle color={theme.placeholder} size={28} />
            </View>
            {filter === 'groups' ? <>
              <SizableText color={theme.text} fontSize={scale(17)} fontWeight="600">Sin grupos</SizableText>
              <Paragraph color={theme.placeholder} textAlign="center">Los grupos que crees aparecerán aquí.</Paragraph>
            </> : filtering ? <>
              <SizableText color={theme.text} fontSize={scale(17)} fontWeight="600">Sin coincidencias</SizableText>
              <Paragraph color={theme.placeholder} textAlign="center">Ninguna conversación coincide con la búsqueda o el filtro activo.</Paragraph>
            </> : <>
              <SizableText color={theme.text} fontSize={scale(17)} fontWeight="600">Tus conversaciones</SizableText>
              <Paragraph color={theme.placeholder} textAlign="center">Añade un contacto para empezar. Tus mensajes y conversaciones se mostrarán en esta lista.</Paragraph>
              <NativeRippleButton onPress={() => setComposerOpen(true)}>Nueva conversación</NativeRippleButton>
              <NativeRipplePressable accessibilityRole="button" onPress={() => router.navigate('/contacts')}><Text color={theme.accent} fontSize={scale(13)} fontWeight="600">Ver contactos</Text></NativeRipplePressable>
            </>}
          </YStack>
        )}
      </ScrollView>
      <ChatContextMenu entry={selectedEntry} onClose={() => setSelectedEntry(null)} onMarkRead={markEntryRead} onToggleFavorite={toggleEntryFavorite} onToggleFlag={toggleEntryFlag} onUnavailable={showUnavailableAction} />
      {menuOpen ? (
        <ChatListMenu
          onClose={() => setMenuOpen(false)}
          onMarkAllRead={markAllRead}
          onNewConversation={() => setComposerOpen(true)}
          onOpenContacts={() => router.navigate('/contacts')}
          onOpenSettings={() => router.navigate('/settings')}
          onSignOut={() => void signOut()}
          onUnavailable={(label) => showSnackbar(`${label} estará disponible próximamente`)}
        />
      ) : null}
      <BottomSheet icon={<MessageSquarePlus color={theme.accent} size={20} />} onClose={() => setComposerOpen(false)} title="Nueva conversación" visible={composerOpen}>
        <NewConversationList contacts={contacts} onPick={(contact) => { setComposerOpen(false); router.push({ pathname: '/chat/[upeerId]', params: { upeerId: contact.upeerId } }); }} onUnavailable={(label) => { setComposerOpen(false); showSnackbar(`${label} estará disponible próximamente`); }} />
      </BottomSheet>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({ root: { flex: 1 }, chatContent: { flexGrow: 1, gap: 12, paddingHorizontal: 20, paddingTop: 20, paddingBottom: 18 }, chatFiltersScroll: { flexGrow: 0 }, chatFilters: { alignItems: 'center', gap: 8 }, chatClearButton: { alignItems: 'center', height: 24, justifyContent: 'center', width: 24 }, chatFilterChip: { alignItems: 'center', borderRadius: 999, justifyContent: 'center', marginTop: 8, minHeight: 30, overflow: 'hidden', paddingHorizontal: 12 }, chatMenuButton: { alignItems: 'center', borderRadius: 22, height: 44, justifyContent: 'center', width: 44 }, emptyAvatar: { alignItems: 'center', borderRadius: 32, height: 64, justifyContent: 'center', width: 64 } });