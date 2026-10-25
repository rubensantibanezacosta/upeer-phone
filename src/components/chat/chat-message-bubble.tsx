import { Pressable, StyleSheet, View } from 'react-native';
import { Text } from 'tamagui';

import type { ChatTimelineEntry } from '@/domain/chat-timeline';
import { formatChatTime } from '@/domain/chat-timeline';
import { parseContactCard, type ContactCard } from '@/domain/contact-card';
import { parseAttachmentMessage } from '@/domain/attachment';
import { parseSystemMessage } from '@/domain/system-message';
import type { ReactionSummary } from '@/domain/chat-reactions';
import type { ReplyPreview } from '@/domain/chat-reply';
import { ChatContactCard } from '@/components/chat/chat-contact-card';
import { ChatAttachment } from '@/components/chat/chat-attachment';
import { ChatSystemMessage } from '@/components/chat/chat-system-message';
import { MessageStatus } from '@/components/chat/message-status';
import { ChatReplyQuote } from '@/components/chat/chat-reply-quote';
import { ChatReactionRow } from '@/components/chat/chat-reaction-row';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';
import type { ProtocolMessage } from '../../../modules/upeer-transport/src/UPeerTransport.types';

export function ChatMessageBubble({ entry, highlighted = false, onLongPress, onOpenCard, onOpenReply, onToggleReaction, onUnavailable, reactions, reply, savedContacts }: {
  entry: ChatTimelineEntry;
  highlighted?: boolean;
  onLongPress?: (message: ProtocolMessage) => void;
  onOpenCard?: (card: ContactCard) => void;
  onOpenReply?: (messageID: string) => void;
  onToggleReaction?: (message: ProtocolMessage, emoji: string) => void;
  onUnavailable?: (label: string) => void;
  reactions?: ReactionSummary[];
  reply?: ReplyPreview | null;
  savedContacts?: ReadonlySet<string>;
}) {
  const theme = useTheme();
  const scale = useTextScale();
  const isMine = entry.message.isMine;
  const textColor = isMine ? theme.bubbleMineText : theme.text;
  const metaColor = isMine ? theme.bubbleMineText : theme.placeholder;
  const deleted = entry.message.deleted === true;
  const card = deleted ? null : parseContactCard(entry.message.content);
  const attachment = deleted || card ? null : parseAttachmentMessage(entry.message.content);
  const quotedID = deleted ? undefined : entry.message.replyTo;
  const onPressReply = onOpenReply && quotedID ? () => onOpenReply(quotedID) : undefined;
  const system = deleted ? null : parseSystemMessage(entry.message.content);

  if (system) {
    return <View style={{ marginTop: entry.startsGroup ? 10 : 4 }}><ChatSystemMessage message={system} /></View>;
  }

  return (
    <View style={[styles.row, { marginTop: entry.startsGroup ? 10 : 4 }, isMine ? styles.rowMine : styles.rowTheirs]}>
      <View style={styles.column}>
        <Pressable
          accessibilityHint="Mantén pulsado para ver las acciones del mensaje"
          onLongPress={onLongPress ? () => onLongPress(entry.message) : undefined}
          style={[
            styles.bubble,
            { backgroundColor: isMine ? theme.bubbleMine : theme.backgroundElement },
            isMine ? styles.bubbleMine : styles.bubbleTheirs,
            highlighted ? { borderColor: theme.accent, borderWidth: 1.5 } : null,
          ]}
        >
          {reply && !deleted ? <View style={styles.quote}><ChatReplyQuote isMine={isMine} onPress={onPressReply} reply={reply} /></View> : null}
          <View style={[styles.body, card || attachment ? styles.bodyColumn : null]}>
            {card && onOpenCard ? (
              <ChatContactCard
                card={card}
                onPress={onOpenCard}
                saved={savedContacts?.has(card.upeerId.toLowerCase()) === true}
                tone={isMine ? 'mine' : 'theirs'}
              />
            ) : attachment ? (
              <ChatAttachment info={attachment} isMine={isMine} onUnavailable={onUnavailable} status={entry.message.status} timestamp={entry.message.timestamp} />
            ) : (
              <Text color={textColor} fontSize={scale(14)} opacity={deleted ? 0.6 : 1} style={[styles.content, deleted ? styles.deletedContent : null]}>{entry.message.content}</Text>
            )}
            {attachment?.kind === 'voice' ? null : (
              <View style={styles.meta}>
                {entry.message.isEdited && !deleted ? <Text color={metaColor} fontSize={scale(10)} opacity={0.8}>editado</Text> : null}
                <Text color={metaColor} fontSize={scale(10)} opacity={0.8}>{formatChatTime(entry.message.timestamp)}</Text>
                {isMine ? <MessageStatus color={metaColor} status={entry.message.status} /> : null}
              </View>
            )}
          </View>
        </Pressable>
        {onToggleReaction ? (
          <ChatReactionRow isMine={isMine} onToggle={(emoji) => onToggleReaction(entry.message, emoji)} reactions={reactions ?? []} />
        ) : null}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', width: '100%' },
  rowMine: { justifyContent: 'flex-end' },
  rowTheirs: { justifyContent: 'flex-start' },
  column: { maxWidth: '80%' },
  bubble: {
    borderRadius: 12,
    elevation: 2,
    paddingBottom: 3,
    paddingHorizontal: 8,
    paddingTop: 5,
    shadowColor: '#000',
    shadowOffset: { height: 2, width: 0 },
    shadowOpacity: 0.35,
    shadowRadius: 8,
  },
  bubbleMine: { borderTopRightRadius: 4 },
  bubbleTheirs: { borderTopLeftRadius: 4 },
  quote: { alignSelf: 'stretch', paddingBottom: 3, paddingTop: 2 },
  deletedContent: { fontStyle: 'italic' },
  body: { alignItems: 'flex-end', columnGap: 8, flexDirection: 'row', minWidth: 80 },
  bodyColumn: { alignItems: 'stretch', flexDirection: 'column', gap: 2, minWidth: 180 },
  content: { flexShrink: 1, paddingBottom: 2 },
  meta: { alignItems: 'center', flexDirection: 'row', gap: 4, marginLeft: 'auto' },
});
