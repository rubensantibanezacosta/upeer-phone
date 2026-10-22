import { Pressable, StyleSheet, View } from 'react-native';
import { Check, CheckCheck, CircleAlert, Clock3 } from '@tamagui/lucide-icons-2';
import { Text } from 'tamagui';

import type { ChatTimelineEntry } from '@/domain/chat-timeline';
import { formatChatTime } from '@/domain/chat-timeline';
import { useTheme } from '@/hooks/use-theme';
import type { ProtocolMessage } from '../../../modules/upeer-transport/src/UPeerTransport.types';

const statusLabels: Record<ProtocolMessage['status'], string> = {
  pending: 'Pendiente',
  sent: 'Enviado',
  delivered: 'Entregado',
  read: 'Leído',
  failed: 'Fallido',
};

function MessageStatus({ color, status }: { color: string; status: ProtocolMessage['status'] }) {
  const theme = useTheme();
  const iconColor = status === 'failed' ? theme.danger : status === 'read' ? theme.receiptRead : color;
  const icon = status === 'failed'
    ? <CircleAlert color={iconColor} size={14} strokeWidth={2.3} />
    : status === 'pending'
      ? <Clock3 color={iconColor} size={12} strokeWidth={2.2} />
      : status === 'sent'
        ? <Check color={iconColor} size={16} strokeWidth={2.3} />
        : <CheckCheck color={iconColor} size={16} strokeWidth={2.3} />;
  return <View accessibilityLabel={`Estado del mensaje: ${statusLabels[status]}`} style={styles.status}>{icon}</View>;
}

export function ChatMessageBubble({ entry, onEdit }: { entry: ChatTimelineEntry; onEdit?: (message: ProtocolMessage) => void }) {
  const theme = useTheme();
  const isMine = entry.message.isMine;
  const textColor = isMine ? theme.bubbleMineText : theme.text;
  const metaColor = isMine ? theme.bubbleMineText : theme.placeholder;
  const editable = isMine && Boolean(onEdit);

  return (
    <Pressable
      accessibilityHint={editable ? 'Mantén pulsado para editar el mensaje' : undefined}
      disabled={!editable}
      onLongPress={editable ? () => onEdit?.(entry.message) : undefined}
      style={[styles.row, { marginTop: entry.startsGroup ? 10 : 4 }, isMine ? styles.rowMine : styles.rowTheirs]}
    >
      <View style={[
        styles.bubble,
        { backgroundColor: isMine ? theme.bubbleMine : theme.backgroundElement },
        isMine ? styles.bubbleMine : styles.bubbleTheirs,
      ]}>
        <View style={styles.body}>
          <Text color={textColor} fontSize={14} style={styles.content}>{entry.message.content}</Text>
          <View style={styles.meta}>
            {entry.message.isEdited ? <Text color={metaColor} fontSize={10} opacity={0.8}>editado</Text> : null}
            <Text color={metaColor} fontSize={10} opacity={0.8}>{formatChatTime(entry.message.timestamp)}</Text>
            {isMine ? <MessageStatus color={metaColor} status={entry.message.status} /> : null}
          </View>
        </View>
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', width: '100%' },
  rowMine: { justifyContent: 'flex-end' },
  rowTheirs: { justifyContent: 'flex-start' },
  bubble: {
    borderRadius: 12,
    elevation: 2,
    maxWidth: '80%',
    paddingBottom: 2,
    paddingHorizontal: 8,
    paddingTop: 4,
    shadowColor: '#000',
    shadowOffset: { height: 2, width: 0 },
    shadowOpacity: 0.35,
    shadowRadius: 8,
  },
  bubbleMine: { borderTopRightRadius: 4 },
  bubbleTheirs: { borderTopLeftRadius: 4 },
  body: { alignItems: 'flex-end', columnGap: 8, flexDirection: 'row', minWidth: 80 },
  content: { flexShrink: 1, paddingBottom: 2 },
  meta: { alignItems: 'center', flexDirection: 'row', gap: 4, marginLeft: 'auto' },
  status: { alignItems: 'center', justifyContent: 'center' },
});
