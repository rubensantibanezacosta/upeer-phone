import { View } from 'react-native';

import { ChatDateSeparator } from '@/components/chat/chat-date-separator';
import { ChatMessageBubble } from '@/components/chat/chat-message-bubble';
import { summarizeReactions } from '@/domain/chat-reactions';
import type { ReplyPreview } from '@/domain/chat-reply';
import type { ChatTimelineEntry } from '@/domain/chat-timeline';
import type { ContactCard } from '@/domain/contact-card';
import type { ProtocolMessage } from '../../../modules/upeer-transport/src/UPeerTransport.types';

type ChatTimelineRowProps = {
  entry: ChatTimelineEntry;
  highlighted: boolean;
  onLongPress?: (message: ProtocolMessage) => void;
  onOpenCard?: (card: ContactCard) => void;
  onOpenReply?: (messageID: string) => void;
  onToggleReaction?: (message: ProtocolMessage, emoji: string) => void;
  onUnavailable?: (label: string) => void;
  reply: ReplyPreview | null;
  savedContacts?: ReadonlySet<string>;
  selfUpeerId: string;
};

export function ChatTimelineRow({ entry, highlighted, onLongPress, onOpenCard, onOpenReply, onToggleReaction, onUnavailable, reply, savedContacts, selfUpeerId }: ChatTimelineRowProps) {
  return (
    <View>
      {entry.showDateSeparator ? <ChatDateSeparator timestamp={entry.message.timestamp} /> : null}
      <ChatMessageBubble
        entry={entry}
        highlighted={highlighted}
        onLongPress={onLongPress}
        onOpenCard={onOpenCard}
        onOpenReply={onOpenReply}
        onToggleReaction={onToggleReaction}
        onUnavailable={onUnavailable}
        reactions={summarizeReactions(entry.message.reactions, selfUpeerId)}
        reply={reply}
        savedContacts={savedContacts}
      />
    </View>
  );
}
