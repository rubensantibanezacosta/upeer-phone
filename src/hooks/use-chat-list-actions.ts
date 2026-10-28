import { useCallback } from 'react';

import type { ChatListEntry } from '@/components/chat-list-item';
import type { useSnackbar } from '@/components/snackbar';
import { flagMessage, type ConversationFlag } from '@/domain/conversation-flags';
import { unreadConversationIds } from '@/domain/chat-unread';
import { setConversationFlag } from '@/storage/conversation-flags-store';
import { getContacts, setContactFavorite, type UPeerContact } from '@/storage/contact-store';
import { deleteConversation, getConversations, markConversationRead } from '../../modules/upeer-transport/src/UPeerTransportModule';
import type { ProtocolConversation } from '../../modules/upeer-transport/src/UPeerTransport.types';

type ChatListActionsOptions = {
  conversations: readonly ProtocolConversation[];
  onContacts: (contacts: UPeerContact[]) => void;
  onConversations: (conversations: ProtocolConversation[]) => void;
  onSelect: (entry: ChatListEntry | null) => void;
  showSnackbar: ReturnType<typeof useSnackbar>['showSnackbar'];
};

export function useChatListActions({ conversations, onContacts, onConversations, onSelect, showSnackbar }: ChatListActionsOptions) {
  const report = useCallback((cause: unknown, message: string) => {
    console.warn('No se pudo completar la acción de la lista de chats.', cause);
    showSnackbar(message);
  }, [showSnackbar]);

  const clearEntry = useCallback((entry: ChatListEntry) => {
    onSelect(null);
    deleteConversation(entry.upeerId)
      .then(getConversations)
      .then((next) => {
        onConversations(next);
        showSnackbar('Conversación vaciada');
      })
      .catch((cause: unknown) => report(cause, 'No se pudo vaciar la conversación'));
  }, [onConversations, onSelect, report, showSnackbar]);

  const toggleEntryFlag = useCallback((entry: ChatListEntry, flag: ConversationFlag) => {
    onSelect(null);
    setConversationFlag(entry.upeerId, flag)
      .then(() => showSnackbar(flagMessage(flag, !entry[flag])))
      .catch((cause: unknown) => report(cause, 'No se pudo cambiar el ajuste de la conversación'));
  }, [onSelect, report, showSnackbar]);

  const toggleEntryFavorite = useCallback((entry: ChatListEntry) => {
    onSelect(null);
    setContactFavorite(entry.upeerId, entry.favorite !== true)
      .then(getContacts)
      .then((list) => {
        onContacts(list);
        showSnackbar(entry.favorite ? 'Quitado de Favoritos' : 'Añadido a Favoritos');
      })
      .catch((cause: unknown) => report(cause, 'No se pudo cambiar el favorito'));
  }, [onContacts, onSelect, report, showSnackbar]);

  const markEntryRead = useCallback((entry: ChatListEntry) => {
    onSelect(null);
    markConversationRead(entry.upeerId)
      .then(getConversations)
      .then(onConversations)
      .catch((cause: unknown) => report(cause, 'No se pudo marcar la conversación como leída'));
  }, [onConversations, onSelect, report]);

  const markAllRead = useCallback(() => {
    const targets = unreadConversationIds(conversations);
    if (targets.length === 0) {
      showSnackbar('No hay conversaciones sin leer');
      return;
    }
    Promise.all(targets.map((upeerId) => markConversationRead(upeerId)))
      .then(getConversations)
      .then((next) => {
        onConversations(next);
        showSnackbar('Todo marcado como leído');
      })
      .catch((cause: unknown) => report(cause, 'No se pudieron marcar como leídas'));
  }, [conversations, onConversations, report, showSnackbar]);

  return { clearEntry, markAllRead, markEntryRead, toggleEntryFavorite, toggleEntryFlag };
}
