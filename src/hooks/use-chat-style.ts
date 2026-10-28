import { useCallback, useEffect, useSyncExternalStore } from 'react';

import { chatStyleFor, getChatStylePreset, type ChatStyleId, type ChatStyleMap } from '@/domain/chat-style';
import { getChatStyles, loadChatStyles, saveChatStyle, subscribeChatStyles } from '@/storage/chat-style-store';

export function useChatStyle(chatId?: string | null) {
  const map: ChatStyleMap = useSyncExternalStore(subscribeChatStyles, getChatStyles, getChatStyles);

  useEffect(() => {
    void loadChatStyles();
  }, []);

  const styleId = chatStyleFor(map, chatId);
  const preset = getChatStylePreset(styleId);
  const set = useCallback((id: string): Promise<ChatStyleId> => (chatId ? saveChatStyle(chatId, id) : Promise.resolve('default' as ChatStyleId)), [chatId]);

  return { preset, set, styleId };
}
