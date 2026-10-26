import { useEffect, useState } from 'react';

import { collectRecentCalls, type CallBatch, type RecentCall } from '@/domain/call-history';
import { useFirstLoad } from '@/hooks/use-first-load';
import type { UPeerContact } from '@/storage/contact-store';
import { getContacts } from '@/storage/contact-store';
import { getConversations, getMessages } from '../../modules/upeer-transport/src/UPeerTransportModule';
import type { ProtocolConversation } from '../../modules/upeer-transport/src/UPeerTransport.types';

const POLL_MS = 5000;
const MESSAGES_PER_CHAT = 200;

export type CallHistoryItem = RecentCall & {
  title: string;
  avatar?: string;
  group: boolean;
};

export function useRecentCalls(): { calls: CallHistoryItem[]; loading: boolean } {
  const [calls, setCalls] = useState<CallHistoryItem[]>([]);
  const { loading, markLoaded } = useFirstLoad();

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      try {
        const [conversations, contacts] = await Promise.all([getConversations(), loadContacts()]);
        const batches: CallBatch[] = await Promise.all(conversations.map(async (conversation) => ({
          chatId: conversation.upeerId,
          messages: await getMessages(conversation.upeerId, MESSAGES_PER_CHAT),
        })));
        const resolved = collectRecentCalls(batches).map((call) => resolveCall(call, conversations, contacts));
        if (!cancelled) setCalls(resolved);
      } catch (cause) {
        console.warn('No se pudo leer el historial de llamadas.', cause);
      } finally {
        if (!cancelled) markLoaded();
      }
    };
    void load();
    const timer = setInterval(() => { void load(); }, POLL_MS);
    return () => { cancelled = true; clearInterval(timer); };
  }, [markLoaded]);

  return { calls, loading };
}

async function loadContacts(): Promise<UPeerContact[]> {
  try {
    return await getContacts();
  } catch (cause) {
    console.warn('No se pudieron leer los contactos para el historial de llamadas.', cause);
    return [];
  }
}

function resolveCall(call: RecentCall, conversations: ProtocolConversation[], contacts: UPeerContact[]): CallHistoryItem {
  const conversation = conversations.find((item) => item.upeerId === call.chatId);
  const contact = contacts.find((item) => item.upeerId === call.chatId);
  const title = conversation?.alias?.trim() || contact?.name?.trim() || call.chatId.slice(0, 12);
  return { ...call, title, avatar: conversation?.avatar, group: conversation?.group === true };
}
