import { useCallback } from 'react';

import { useCopyToClipboard } from '@/hooks/use-copy-to-clipboard';
import { useMessageActions } from '@/hooks/use-message-actions';
import type { ProtocolMessage } from '../../modules/upeer-transport/src/UPeerTransport.types';

type ChatMessageSheetOptions = {
  onChanged: () => Promise<void>;
  onEdit: (message: ProtocolMessage) => void;
  onError: (message: string) => void;
  onReply: (message: ProtocolMessage) => void;
  upeerId: string | undefined;
};

export function useChatMessageSheet({ onChanged, onEdit, onError, onReply, upeerId }: ChatMessageSheetOptions) {
  const copy = useCopyToClipboard();
  const { close, open, remove, selected, selfUpeerId, toggleReaction } = useMessageActions(upeerId, { onChanged, onError });

  const editMessage = useCallback((message: ProtocolMessage) => {
    close();
    onEdit(message);
  }, [close, onEdit]);

  const replyMessage = useCallback((message: ProtocolMessage) => {
    close();
    onReply(message);
  }, [close, onReply]);

  const copyMessage = useCallback((message: ProtocolMessage) => {
    close();
    void copy(message.content);
  }, [close, copy]);

  return {
    close,
    copyMessage,
    editMessage,
    open,
    remove,
    replyMessage,
    selected,
    selfUpeerId,
    toggleReaction,
  };
}
