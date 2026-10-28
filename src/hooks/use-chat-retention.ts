import { useCallback, useEffect, useSyncExternalStore } from 'react';

import { retentionFor, retentionNotice, type RetentionId, type RetentionMap } from '@/domain/retention';
import { getChatRetention, loadChatRetention, saveChatRetention, subscribeChatRetention } from '@/storage/retention-store';

export function useChatRetention(chatId?: string | null) {
  const map: RetentionMap = useSyncExternalStore(subscribeChatRetention, getChatRetention, getChatRetention);

  useEffect(() => {
    void loadChatRetention();
  }, []);

  const retention = retentionFor(map, chatId);
  const notice = retentionNotice(retention);
  const set = useCallback((id: string): Promise<RetentionId> => (chatId ? saveChatRetention(chatId, id) : Promise.resolve('off')), [chatId]);

  return { notice, retention, set };
}
