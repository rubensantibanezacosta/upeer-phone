import { useEffect, useMemo, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { router } from 'expo-router';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ellipsis, MessageCircle, MessageSquarePlus, Search, Settings, Users } from '@tamagui/lucide-icons-2';
import { H1, Input, Paragraph, SizableText, Text, XStack, YStack } from 'tamagui';

import { ChatContextMenu } from '@/components/chat-context-menu';
import { BottomSheet } from '@/components/bottom-sheet';
import { NewConversationList } from '@/components/new-conversation-list';
import { ChatListItem, type ChatListEntry } from '@/components/chat-list-item';
import { CHAT_LIST_FILTERS, filterChatEntries, isChatFilterActive, type ChatListFilter } from '@/domain/chat-list-filters';
import { flagMessage, flagsFor, orderByPinned, type ConversationFlag } from '@/domain/conversation-flags';
import { useConversationFlags } from '@/hooks/use-conversation-flags';
import { setConversationFlag } from '@/storage/conversation-flags-store';
import { classifyConversations } from '@/domain/incoming-requests';
import { useSnackbar } from '@/components/snackbar';
import { useTheme } from '@/hooks/use-theme';
import type { UPeerContact } from '@/storage/contact-store';
import { getContacts } from '@/storage/contact-store';
import type { ProtocolConversation } from '../../../../modules/upeer-transport/src/UPeerTransport.types';
import { getConversations, markConversationRead } from '../../../../modules/upeer-transport/src/UPeerTransportModule';
import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { NativeRippleButton } from '@/components/native-ripple-button';

const contactStatusLabels: Record<UPeerContact['status'], string> = {
  pending: 'Esperando respuesta…',
  incoming: 'Solicitud de contacto recibida',
  connected: 'Contacto conectado',
  offline: 'Desconectado',
  blocked: 'Bloqueado',
};

export default function ChatsRoute() {
  const theme = useTheme();
  const { showSnackbar } = useSnackbar();
  const conversationFlags = useConversationFlags();
  const [contacts, setContacts] = useState<UPeerContact[]>([]);
  const [conversations, setConversations] = useState<ProtocolConversation[]>([]);
  const [selectedEntry, setSelectedEntry] = useState<ChatListEntry | null>(null);
  const [menuOpen, setMenuOpen] = useState(false);
  const [composerOpen, setComposerOpen] = useState(false);
  const [filter, setFilter] = useState<ChatListFilter>('all');
  const [query, setQuery] = useState('');

  useEffect(() => {
    getContacts().then(setContacts).catch((cause: unknown) => console.warn('No se pudieron cargar los contactos para Chats.', cause));
    const loadConversations = () => {
      getConversations().then(setConversations).catch((cause: unknown) => console.warn('No se pudieron cargar las conversaciones nativas.', cause));
    };
    loadConversations();
    const timer = setInterval(loadConversations, 2000);
    return () => clearInterval(timer);
  }, []);

  const partition = useMemo(() => classifyConversations(conversations, contacts), [contacts, conversations]);
  const requestIds = useMemo(() => new Set(partition.requests.map((request) => request.upeerId)), [partition.requests]);

  const entries: ChatListEntry[] = useMemo(() => (conversations.length > 0
    ? conversations
      .filter((conversation) => !partition.hidden.some((hidden) => hidden.upeerId === conversation.upeerId))
      .map((conversation) => ({
        upeerId: conversation.upeerId,
        title: conversation.alias || conversation.upeerId.slice(0, 12),
        preview: conversation.lastMessage.replace(/^__SYS__\|/, '') || 'Sin mensajes',
        timestamp: conversation.lastTimestamp,
        unread: conversation.unread,
        isMine: conversation.lastIsMine === true,
        lastStatus: conversation.lastStatus ?? null,
        incoming: requestIds.has(conversation.upeerId),
        ...flagsFor(conversationFlags, conversation.upeerId),
      }))
    : contacts.filter((contact) => contact.status !== 'blocked').map((contact) => ({
      upeerId: contact.upeerId,
      title: contact.name,
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

  return (
    <SafeAreaView edges={['top']} style={[styles.root, { backgroundColor: theme.background }]}>
      <ScrollView contentContainerStyle={styles.chatContent}>
        <XStack alignItems="center" justifyContent="space-between">
          <H1 color={theme.text} fontSize={24} fontWeight="700">uPeer</H1>
          <XStack alignItems="center" gap={4}>
            <NativeRipplePressable accessibilityLabel="Nueva conversación" accessibilityRole="button" onPress={() => setComposerOpen(true)} style={styles.chatMenuButton}><MessageSquarePlus color={theme.text} size={21} /></NativeRipplePressable>
            <NativeRipplePressable accessibilityLabel="Más opciones" accessibilityRole="button" onPress={() => setMenuOpen((value) => !value)} style={styles.chatMenuButton}><Ellipsis color={theme.text} size={22} /></NativeRipplePressable>
          </XStack>
        </XStack>
        <XStack alignItems="center" backgroundColor={theme.backgroundElement} borderColor={theme.border} borderRadius={14} borderWidth={1} gap={10} paddingHorizontal={14} paddingVertical={11}>
          <Search color={theme.placeholder} size={17} />
          <Input backgroundColor="transparent" borderWidth={0} color={theme.text} flex={1} fontSize={14} height={22} onChangeText={setQuery} padding={0} placeholder="Buscar conversaciones" placeholderTextColor={theme.placeholder} value={query} />
        </XStack>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.chatFiltersScroll} contentContainerStyle={styles.chatFilters}>
          {CHAT_LIST_FILTERS.map((item) => <NativeRipplePressable key={item.id} onPress={() => setFilter(item.id)} style={[styles.chatFilterChip, { backgroundColor: filter === item.id ? theme.backgroundSelected : theme.background, borderColor: filter === item.id ? theme.accent : theme.border }]}><Text color={filter === item.id ? theme.accent : theme.placeholder} fontSize={11} fontWeight={filter === item.id ? '700' : '500'}>{item.label}</Text>
          </NativeRipplePressable>)}
        </ScrollView>
        {visibleEntries.length > 0 ? (
          <YStack marginTop={2}>
            {visibleEntries.map((entry) => <ChatListItem entry={entry} key={entry.upeerId} onOpen={openChat} onOpenMenu={openContextMenu} />)}
          </YStack>
        ) : (
          <YStack alignItems="center" flex={1} gap={12} justifyContent="center" padding={24}>
            <View style={[styles.emptyAvatar, { backgroundColor: theme.backgroundElement }]}>
              <MessageCircle color={theme.placeholder} size={28} />
            </View>
            {filtering ? <>
              <SizableText color={theme.text} fontSize={17} fontWeight="600">Sin coincidencias</SizableText>
              <Paragraph color={theme.placeholder} lineHeight={24} textAlign="center">Ninguna conversación coincide con la búsqueda o el filtro activo.</Paragraph>
            </> : <>
              <SizableText color={theme.text} fontSize={17} fontWeight="600">Tus conversaciones</SizableText>
              <Paragraph color={theme.placeholder} lineHeight={24} textAlign="center">Añade un contacto para empezar. Tus mensajes y conversaciones se mostrarán en esta lista.</Paragraph>
              <NativeRippleButton onPress={() => setComposerOpen(true)}>Nueva conversación</NativeRippleButton>
              <NativeRipplePressable accessibilityRole="button" onPress={() => router.navigate('/contacts')}><Text color={theme.accent} fontSize={13} fontWeight="600">Ver contactos</Text></NativeRipplePressable>
            </>}
          </YStack>
        )}
      </ScrollView>
      <ChatContextMenu entry={selectedEntry} onClose={() => setSelectedEntry(null)} onMarkRead={markEntryRead} onToggleFlag={toggleEntryFlag} onUnavailable={showUnavailableAction} />
      {menuOpen ? <><Pressable accessibilityLabel="Cerrar menú" onPress={() => setMenuOpen(false)} style={styles.menuDismiss} /><View style={[styles.chatMenu, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}><NativeRipplePressable accessibilityRole="button" onPress={() => { setMenuOpen(false); router.navigate('/contacts'); }} style={styles.chatMenuItem}><Users color={theme.text} size={18} /><Text color={theme.text} fontSize={14}>Contactos</Text></NativeRipplePressable><NativeRipplePressable accessibilityRole="button" onPress={() => { setMenuOpen(false); router.navigate('/settings'); }} style={styles.chatMenuItem}><Settings color={theme.text} size={18} /><Text color={theme.text} fontSize={14}>Configuración</Text></NativeRipplePressable></View></> : null}
      <BottomSheet icon={<MessageSquarePlus color={theme.accent} size={20} />} onClose={() => setComposerOpen(false)} title="Nueva conversación" visible={composerOpen}>
        <NewConversationList contacts={contacts} onPick={(contact) => { setComposerOpen(false); router.push({ pathname: '/chat/[upeerId]', params: { upeerId: contact.upeerId } }); }} onUnavailable={(label) => { setComposerOpen(false); showSnackbar(`${label} estará disponible próximamente`); }} />
      </BottomSheet>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({ root: { flex: 1 }, chatContent: { flexGrow: 1, gap: 12, paddingHorizontal: 20, paddingTop: 20, paddingBottom: 18 }, chatFiltersScroll: { flexGrow: 0 }, chatFilters: { alignItems: 'center', gap: 8 }, chatFilterChip: { alignItems: 'center', borderRadius: 18, borderWidth: StyleSheet.hairlineWidth, justifyContent: 'center', marginTop: 8, minHeight: 30, overflow: 'hidden', paddingHorizontal: 15 }, chatMenuButton: { alignItems: 'center', borderRadius: 22, height: 44, justifyContent: 'center', width: 44 }, menuDismiss: { ...StyleSheet.absoluteFill, zIndex: 1 }, chatMenu: { borderRadius: 12, borderWidth: StyleSheet.hairlineWidth, elevation: 8, paddingVertical: 6, position: 'absolute', right: 20, top: 72, width: 190, zIndex: 2 }, chatMenuItem: { alignItems: 'center', flexDirection: 'row', gap: 12, minHeight: 46, paddingHorizontal: 14 }, emptyAvatar: { alignItems: 'center', borderRadius: 32, height: 64, justifyContent: 'center', width: 64 } });