import { useEffect, useMemo, useState } from 'react';
import { ScrollView, StyleSheet } from 'react-native';
import { router } from 'expo-router';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ellipsis, MessageSquarePlus, UserPlus } from '@tamagui/lucide-icons-2';
import { H1, XStack, YStack } from 'tamagui';

import { ChatContextMenu } from '@/components/chat-context-menu';
import { ChatFilterChips } from '@/components/chat-filter-chips';
import { ChatListChatsSheet } from '@/components/chat-list-chats-sheet';
import { ChatListEmptyState } from '@/components/chat-list-empty-state';
import { ChatListMenu } from '@/components/chat-list-menu';
import { StarredGlobalSheet } from '@/components/starred-global-sheet';
import { ChatListsSheet } from '@/components/chat-lists-sheet';
import { BottomSheet } from '@/components/bottom-sheet';
import { ContactFormSheet, type ContactDraft } from '@/components/contact-form-sheet';
import { saveContactDraft } from '@/services/contact-actions';
import { LoadingState } from '@/components/loading-state';
import { useFirstLoad } from '@/hooks/use-first-load';
import { useChatListActions } from '@/hooks/use-chat-list-actions';
import { NewConversationList } from '@/components/new-conversation-list';
import { GroupComposerSheet } from '@/components/group-composer-sheet';
import { ChatListItem, type ChatListEntry } from '@/components/chat-list-item';
import { filterChatEntries, isChatFilterActive, type ChatListFilter } from '@/domain/chat-list-filters';
import { findChatList, restrictToChatList } from '@/domain/chat-lists';
import { useChatLists } from '@/hooks/use-chat-lists';
import { useChatRetention } from '@/hooks/use-chat-retention';
import { previewMessage } from '@/domain/message-preview';
import { reactionActor, reactionPreview } from '@/domain/reaction-preview';
import { flagsFor, orderByPinned } from '@/domain/conversation-flags';
import { useConversationFlags } from '@/hooks/use-conversation-flags';
import { useNow } from '@/hooks/use-now';
import { classifyConversations } from '@/domain/incoming-requests';
import { useSnackbar } from '@/components/snackbar';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';
import type { UPeerContact } from '@/storage/contact-store';
import { getContacts } from '@/storage/contact-store';
import type { ProtocolConversation, ProtocolGroup } from '../../../../modules/upeer-transport/src/UPeerTransport.types';
import { SearchField } from '@/components/search-field';
import { SearchResultsView } from '@/components/search-results-view';
import { getConversations, getGroups } from '../../../../modules/upeer-transport/src/UPeerTransportModule';
import { useGlobalMessageSearch } from '@/hooks/use-global-message-search';
import { useAndroidBack } from '@/hooks/use-android-back';
import { buildSearchSections, type SearchContact, type SearchMessage, type SearchTarget } from '@/domain/global-search';
import { useSession } from '@/components/session-shell';
import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { SyncProgressBanner } from '@/components/sync-progress-banner';

export default function ChatsRoute() {
  const theme = useTheme();
  const scale = useTextScale();
  const { showSnackbar } = useSnackbar();
  const conversationFlags = useConversationFlags();
  const { identity, profile, signOut } = useSession();
  const now = useNow();
  const [contacts, setContacts] = useState<UPeerContact[]>([]);
  const [conversations, setConversations] = useState<ProtocolConversation[]>([]);
  const [groups, setGroups] = useState<ProtocolGroup[]>([]);
  const [selectedEntry, setSelectedEntry] = useState<ChatListEntry | null>(null);
  const [menuOpen, setMenuOpen] = useState(false);
  const [composerOpen, setComposerOpen] = useState(false);
  const [addingContact, setAddingContact] = useState(false);
  const [creatingGroup, setCreatingGroup] = useState(false);
  const [filter, setFilter] = useState<ChatListFilter>('all');
  const [activeListId, setActiveListId] = useState<string | null>(null);
  const [listsOpen, setListsOpen] = useState(false);
  const [starredOpen, setStarredOpen] = useState(false);
  const [editingListId, setEditingListId] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const { loading, markLoaded } = useFirstLoad();
  const chatLists = useChatLists();
  useChatRetention();
  const activeList = findChatList(chatLists, activeListId);

  useEffect(() => {
    const loadContacts = () => {
      getContacts()
        .then(setContacts)
        .catch((cause: unknown) => console.warn('No se pudieron cargar los contactos para Chats.', cause));
    };
    const loadConversations = () => {
      getConversations()
        .then((next) => {
          setConversations(next);
          markLoaded();
        })
        .catch((cause: unknown) => console.warn('No se pudieron cargar las conversaciones nativas.', cause));
    };
    loadContacts();
    loadConversations();
    getGroups().then(setGroups).catch((cause: unknown) => console.warn('No se pudieron cargar los grupos para la búsqueda.', cause));
    const timer = setInterval(() => {
      loadContacts();
      loadConversations();
    }, 2000);
    return () => clearInterval(timer);
  }, [markLoaded]);

  const addContact = async (draft: ContactDraft) => {
    await saveContactDraft(identity.upeerId, draft);
    setContacts(await getContacts());
    showSnackbar('Contacto añadido');
  };
  const partition = useMemo(() => classifyConversations(conversations, contacts), [contacts, conversations]);
  const requestIds = useMemo(() => new Set(partition.requests.map((request) => request.upeerId)), [partition.requests]);
  const actorNames = useMemo(
    () => new Map(contacts.filter((contact) => contact.name.trim() !== '').map((contact) => [contact.upeerId.toLowerCase(), contact.name.trim()])),
    [contacts],
  );

  const entries: ChatListEntry[] = useMemo(() => conversations
    .filter((conversation) => !partition.hidden.some((hidden) => hidden.upeerId === conversation.upeerId))
    .map((conversation) => {
      const lastReaction = conversation.lastReaction;
      const actor = reactionActor(lastReaction?.actorUpeerId, actorNames, identity.upeerId, profile?.alias);
      const reactionName = actor.actorName || (conversation.group === true ? '' : conversation.alias);
      const reaction = lastReaction
        ? reactionPreview({
          emoji: lastReaction.emoji,
          message: lastReaction.message,
          actorName: actor.actorIsMe ? '' : reactionName,
          actorIsMe: actor.actorIsMe,
        })
        : null;
      return {
        upeerId: conversation.upeerId,
        title: conversation.alias || conversation.upeerId.slice(0, 12),
        avatar: conversation.avatar,
        preview: reaction ?? previewMessage(conversation.lastMessage),
        timestamp: conversation.lastReaction?.timestamp ?? conversation.lastTimestamp,
        unread: conversation.unread,
        isMine: reaction ? false : conversation.lastIsMine === true,
        lastStatus: reaction ? null : conversation.lastStatus ?? null,
        reaction: reaction !== null,
        incoming: requestIds.has(conversation.upeerId),
        favorite: contacts.some((contact) => contact.upeerId === conversation.upeerId && contact.isFavorite === true),
        activity: conversation.activity ?? '',
        lastActive: conversation.lastActive,
        group: conversation.group === true,
        members: conversation.members,
        ...flagsFor(conversationFlags, conversation.upeerId),
      };
    }), [actorNames, contacts, conversationFlags, conversations, identity.upeerId, partition.hidden, profile?.alias, requestIds]);

  const chatTitles = useMemo(() => Object.fromEntries(entries.map((item) => [item.upeerId, item.title])), [entries]);

  const visibleEntries = useMemo(
    () => orderByPinned(filterChatEntries(restrictToChatList(entries, activeList), filter, query), conversationFlags),
    [activeList, conversationFlags, entries, filter, query],
  );
  const filtering = isChatFilterActive(filter, query) || activeList !== null;
  const searchActive = query.trim().length > 0;

  const messageHits = useGlobalMessageSearch(query);

  const searchSections = useMemo(() => {
    const titles = new Map(entries.map((entry) => [entry.upeerId, entry.title]));
    const searchContacts: SearchContact[] = contacts.map((contact) => ({
      upeerId: contact.upeerId,
      name: contact.name,
      address: contact.address,
      blocked: contact.status === 'blocked',
      favorite: contact.isFavorite === true,
    }));
    const searchMessages: SearchMessage[] = messageHits.map((hit) => ({
      ...hit,
      chatTitle: titles.get(hit.chatUpeerId) ?? hit.chatUpeerId.slice(0, 12),
    }));
    return buildSearchSections(query, filter, {
      chats: entries.map((entry) => ({
        upeerId: entry.upeerId,
        title: entry.title,
        preview: entry.preview,
        unread: entry.unread,
        timestamp: entry.timestamp,
        favorite: entry.favorite,
        pinned: entry.pinned,
        group: entry.group,
      })),
      contacts: searchContacts,
      groups: groups.map((group) => ({ groupId: group.groupId, name: group.name, members: group.members, updatedAt: group.updatedAt ?? group.createdAt ?? 0 })),
      messages: searchMessages,
    });
  }, [contacts, entries, filter, groups, messageHits, query]);

  useAndroidBack(() => {
    if (!searchActive) return false;
    setQuery('');
    return true;
  });

  const openSearchResult = (target: SearchTarget) => {
    if (target.kind === 'contact') {
      router.push({ pathname: '/contact/[upeerId]', params: { upeerId: target.upeerId } });
      return;
    }
    if (target.kind === 'group') {
      router.push({ pathname: '/chat/[upeerId]', params: { upeerId: target.groupId } });
      return;
    }
    router.push({ pathname: '/chat/[upeerId]', params: target.messageId ? { upeerId: target.upeerId, messageId: target.messageId } : { upeerId: target.upeerId } });
  };

  const openChat = (entry: ChatListEntry) => {
    setSelectedEntry(null);
    router.push({ pathname: '/chat/[upeerId]', params: { upeerId: entry.upeerId } });
  };

  const openContextMenu = (entry: ChatListEntry) => setSelectedEntry(entry);

  const { clearEntry, markAllRead, markEntryRead, toggleEntryFavorite, toggleEntryFlag } = useChatListActions({
    conversations,
    onContacts: setContacts,
    onConversations: setConversations,
    onSelect: setSelectedEntry,
    showSnackbar,
  });

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
        <ChatFilterChips
          activeListId={activeListId}
          filter={filter}
          lists={chatLists}
          onManageLists={() => setListsOpen(true)}
          onSelectFilter={(next) => { setActiveListId(null); setFilter(next); }}
          onSelectList={(listId) => { setActiveListId(listId); setFilter('all'); }}
        />
        {searchActive ? (
          <SearchResultsView
            loading={loading}
            onClearQuery={() => setQuery('')}
            onOpenRow={openSearchResult}
            onResetFilters={() => setFilter('all')}
            query={query}
            sections={searchSections}
          />
        ) : visibleEntries.length > 0 ? (
          <YStack marginTop={2}>
            {visibleEntries.map((entry) => <ChatListItem entry={entry} key={entry.upeerId} now={now} onOpen={openChat} onOpenMenu={openContextMenu} />)}
          </YStack>
        ) : loading ? (
          <LoadingState compact label="Cargando conversaciones…" />
        ) : (
          <ChatListEmptyState filter={filter} filtering={filtering} onNewConversation={() => setComposerOpen(true)} />
        )}
      </ScrollView>
      <ChatContextMenu entry={selectedEntry} onClear={clearEntry} onClose={() => setSelectedEntry(null)} onMarkRead={markEntryRead} onToggleFavorite={toggleEntryFavorite} onToggleFlag={toggleEntryFlag} />
      {menuOpen ? (
        <ChatListMenu
          onAddContact={() => setAddingContact(true)}
          onClose={() => setMenuOpen(false)}
          onMarkAllRead={markAllRead}
          onNewConversation={() => setComposerOpen(true)}
          onOpenLists={() => setListsOpen(true)}
          onOpenStarred={() => setStarredOpen(true)}
          onOpenSettings={() => router.navigate('/settings')}
          onSignOut={() => void signOut()}
          onUnavailable={(label) => showSnackbar(`${label} estará disponible próximamente`)}
        />
      ) : null}
      <ChatListsSheet
        onClose={() => setListsOpen(false)}
        onEditChats={(list) => { setListsOpen(false); setEditingListId(list.id); }}
        visible={listsOpen}
      />
      <ChatListChatsSheet
        chats={entries.map((entry) => ({ title: entry.title, upeerId: entry.upeerId }))}
        list={findChatList(chatLists, editingListId)}
        onClose={() => setEditingListId(null)}
      />
      <StarredGlobalSheet
        onClose={() => setStarredOpen(false)}
        onOpen={(chatId, messageId) => router.push({ pathname: '/chat/[upeerId]', params: { messageId, upeerId: chatId } })}
        titles={chatTitles}
        visible={starredOpen}
      />
      <BottomSheet icon={<MessageSquarePlus color={theme.accent} size={20} />} onClose={() => setComposerOpen(false)} title="Nueva conversación" visible={composerOpen}>
        <NewConversationList
          contacts={contacts}
          onAddContact={() => { setComposerOpen(false); setAddingContact(true); }}
          onCreateGroup={() => { setComposerOpen(false); setCreatingGroup(true); }}
          onPick={(contact) => { setComposerOpen(false); router.push({ pathname: '/chat/[upeerId]', params: { upeerId: contact.upeerId } }); }}
          onUnavailable={(label) => { setComposerOpen(false); showSnackbar(`${label} estará disponible próximamente`); }}
        />
      </BottomSheet>
      <GroupComposerSheet
        contacts={contacts}
        onClose={() => setCreatingGroup(false)}
        onCreated={(groupId) => {
          setCreatingGroup(false);
          showSnackbar('Grupo creado');
          router.push({ pathname: '/chat/[upeerId]', params: { upeerId: groupId } });
        }}
        onError={showSnackbar}
        visible={creatingGroup}
      />
      <BottomSheet icon={<UserPlus color={theme.accent} size={20} />} onClose={() => setAddingContact(false)} title="Añadir contacto" visible={addingContact}>
        <ContactFormSheet onAdd={addContact} onDone={() => setAddingContact(false)} />
      </BottomSheet>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({ root: { flex: 1 }, chatContent: { flexGrow: 1, gap: 12, paddingHorizontal: 20, paddingTop: 20, paddingBottom: 18 }, chatClearButton: { alignItems: 'center', height: 24, justifyContent: 'center', width: 24 }, chatMenuButton: { alignItems: 'center', borderRadius: 22, height: 44, justifyContent: 'center', width: 44 }, emptyAvatar: { alignItems: 'center', borderRadius: 32, height: 64, justifyContent: 'center', width: 64 } });