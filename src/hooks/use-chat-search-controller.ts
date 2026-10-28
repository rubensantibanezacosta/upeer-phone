import { useEffect } from 'react';

import { useChatSearch } from '@/hooks/use-chat-search';
import { useChatSearchJump } from '@/hooks/use-chat-search-jump';
import { consumeChatSearch, subscribeChatSearch } from '@/storage/chat-search-request';
import type { ProtocolMessage } from '../../modules/upeer-transport/src/UPeerTransport.types';

type UseChatSearchControllerOptions = {
  chatId: string;
  loaded: boolean;
  messages: readonly ProtocolMessage[];
  onJump: (messageID: string) => void;
  onMerge: (page: readonly ProtocolMessage[]) => void;
  onMissing: () => void;
};

export function useChatSearchController({ chatId, loaded, messages, onJump, onMerge, onMissing }: UseChatSearchControllerOptions) {
  const jump = useChatSearchJump({ chatId, loaded, messages, onJump, onMerge, onMissing });
  const search = useChatSearch({ chatId, onJump: jump });
  const open = search.open;

  useEffect(() => {
    if (!chatId) return;
    const openIfPending = () => {
      if (consumeChatSearch(chatId)) open();
    };
    openIfPending();
    return subscribeChatSearch(openIfPending);
  }, [chatId, open]);

  return search;
}
