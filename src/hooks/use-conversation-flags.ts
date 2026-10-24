import { useEffect, useSyncExternalStore } from 'react';

import type { ConversationFlagsMap } from '@/domain/conversation-flags';
import { getConversationFlags, loadConversationFlags, subscribeConversationFlags } from '@/storage/conversation-flags-store';

export function useConversationFlags(): ConversationFlagsMap {
  const flags = useSyncExternalStore(subscribeConversationFlags, getConversationFlags, getConversationFlags);
  useEffect(() => {
    void loadConversationFlags();
  }, []);
  return flags;
}
