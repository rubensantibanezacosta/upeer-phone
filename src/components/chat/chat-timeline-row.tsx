import { StyleSheet, View } from 'react-native';

import { ChatDateSeparator } from '@/components/chat/chat-date-separator';
import { ChatMessageBubble } from '@/components/chat/chat-message-bubble';
import type { AttachmentInfo } from '@/domain/attachment';
import { summarizeReactions } from '@/domain/chat-reactions';
import type { ReplyPreview } from '@/domain/chat-reply';
import type { ChatTimelineEntry } from '@/domain/chat-timeline';
import type { ContactCard } from '@/domain/contact-card';
import { useTheme } from '@/hooks/use-theme';
import type { ProtocolMessage } from '../../../modules/upeer-transport/src/UPeerTransport.types';

type ChatTimelineRowProps = {
  entry: ChatTimelineEntry;
  highlighted: boolean;
  onLongPress?: (message: ProtocolMessage) => void;
  onOpenAttachment?: (info: AttachmentInfo) => void;
  onOpenCard?: (card: ContactCard) => void;
  onOpenReply?: (messageID: string) => void;
  onToggleReaction?: (message: ProtocolMessage, emoji: string) => void;
  reply: ReplyPreview | null;
  savedContacts?: ReadonlySet<string>;
  selfUpeerId: string;
};

export function ChatTimelineRow({ entry, highlighted, onLongPress, onOpenAttachment, onOpenCard, onOpenReply, onToggleReaction, reply, savedContacts, selfUpeerId }: ChatTimelineRowProps) {
  const theme = useTheme();
  return (
    <View style={highlighted ? [styles.highlight, { backgroundColor: theme.accentSoft }] : undefined}>
      {entry.showDateSeparator ? <ChatDateSeparator timestamp={entry.message.timestamp} /> : null}
      <ChatMessageBubble
        entry={entry}
        highlighted={highlighted}
        onLongPress={onLongPress}
        onOpenAttachment={onOpenAttachment}
        onOpenCard={onOpenCard}
        onOpenReply={onOpenReply}
        onToggleReaction={onToggleReaction}
        reactions={summarizeReactions(entry.message.reactions, selfUpeerId)}
        reply={reply}
        savedContacts={savedContacts}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  highlight: { borderRadius: 12 },
});
