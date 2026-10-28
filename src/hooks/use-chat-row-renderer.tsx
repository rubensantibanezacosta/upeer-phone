import { useCallback } from 'react';
import type { ListRenderItemInfo } from 'react-native';

import { ChatTimelineRow } from '@/components/chat/chat-timeline-row';
import type { ChatTimelineEntry } from '@/domain/chat-timeline';
import type { AttachmentInfo } from '@/domain/attachment';
import type { ContactCard } from '@/domain/contact-card';
import type { ReplyPreview } from '@/domain/chat-reply';
import type { ProtocolMessage } from '../../modules/upeer-transport/src/UPeerTransport.types';

type ChatRowRendererOptions = {
  highlightedID: string | null;
  highlightedMessageId: string | null;
  onLongPress: (message: ProtocolMessage) => void;
  onOpenAttachment: (info: AttachmentInfo, messageId: string, isMine: boolean) => void;
  onOpenCard: (card: ContactCard) => void;
  onScrollToMessage: (messageID: string) => void;
  onToggleReaction: (message: ProtocolMessage, emoji: string) => void;
  onToggleSelect: (message: ProtocolMessage) => void;
  replyPreviews: (message: ProtocolMessage) => ReplyPreview | null;
  savedContactIds: ReadonlySet<string>;
  selected: ReadonlySet<string>;
  selectable: boolean;
  selfUpeerId: string;
};

export function useChatRowRenderer(options: ChatRowRendererOptions) {
  const {
    highlightedID,
    highlightedMessageId,
    onLongPress,
    onOpenAttachment,
    onOpenCard,
    onScrollToMessage,
    onToggleReaction,
    onToggleSelect,
    replyPreviews,
    savedContactIds,
    selectable,
    selected,
    selfUpeerId,
  } = options;

  return useCallback(({ item }: ListRenderItemInfo<ChatTimelineEntry>) => (
    <ChatTimelineRow
      entry={item}
      highlighted={item.message.id === highlightedID || item.message.id === highlightedMessageId}
      onLongPress={onLongPress}
      onOpenAttachment={onOpenAttachment}
      onOpenCard={onOpenCard}
      onOpenReply={onScrollToMessage}
      onToggleReaction={onToggleReaction}
      onToggleSelect={onToggleSelect}
      reply={replyPreviews(item.message)}
      savedContacts={savedContactIds}
      selectable={selectable}
      selected={selected.has(item.message.id)}
      selfUpeerId={selfUpeerId}
    />
  ), [highlightedID, highlightedMessageId, onLongPress, onOpenAttachment, onOpenCard, onScrollToMessage, onToggleReaction, onToggleSelect, replyPreviews, savedContactIds, selectable, selected, selfUpeerId]);
}
