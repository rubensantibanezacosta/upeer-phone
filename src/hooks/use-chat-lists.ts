import { useEffect, useSyncExternalStore } from 'react';

import type { ChatList } from '@/domain/chat-lists';
import { getChatLists, loadChatLists, subscribeChatLists } from '@/storage/chat-lists-store';

export function useChatLists(): ChatList[] {
  const lists = useSyncExternalStore(subscribeChatLists, getChatLists, getChatLists);
  useEffect(() => {
    void loadChatLists();
  }, []);
  return lists;
}
