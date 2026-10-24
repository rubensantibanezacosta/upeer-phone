import { useRef } from 'react';
import { StyleSheet } from 'react-native';
import { BellOff, Check, CheckCheck, CircleAlert, Clock3, Pin, Star } from '@tamagui/lucide-icons-2';
import { SizableText, Text, XStack, YStack } from 'tamagui';

import { ContactTrustAvatar } from '@/components/contact-trust-avatar';
import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { describeChatPreview } from '@/domain/chat-preview';
import { getTrustLabel } from '@/domain/trust';
import type { ProtocolMessage } from '../../modules/upeer-transport/src/UPeerTransport.types';
import { usePreferences } from '@/hooks/use-preferences';
import { useTheme } from '@/hooks/use-theme';

export type ChatListEntry = {
  upeerId: string;
  title: string;
  preview: string;
  timestamp: number;
  unread: number;
  isMine: boolean;
  lastStatus: ProtocolMessage['status'] | null;
  pending?: boolean;
  incoming?: boolean;
  vouchScore?: number;
  archived?: boolean;
  muted?: boolean;
  pinned?: boolean;
  favorite?: boolean;
  typing?: boolean;
  lastSeen?: number;
};

type ChatListItemProps = {
  entry: ChatListEntry;
  now: number;
  onOpen: (entry: ChatListEntry) => void;
  onOpenMenu: (entry: ChatListEntry) => void;
};

function formatConversationTime(timestamp: number): string {
  if (!Number.isFinite(timestamp) || timestamp <= 0) return '';
  const date = new Date(timestamp);
  const now = new Date();
  if (date.toDateString() === now.toDateString()) return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  return date.toLocaleDateString();
}

function LastMessageStatus({ entry, color }: { entry: ChatListEntry; color: string }) {
  const theme = useTheme();
  if (!entry.isMine || !entry.lastStatus) return null;
  if (entry.lastStatus === 'failed') return <CircleAlert color={theme.danger} size={15} strokeWidth={2.2} />;
  if (entry.lastStatus === 'pending') return <Clock3 color={color} size={14} strokeWidth={2.2} />;
  if (entry.lastStatus === 'sent') return <Check color={color} size={15} strokeWidth={2.2} />;
  return <CheckCheck color={entry.lastStatus === 'read' ? theme.accent : color} size={15} strokeWidth={2.2} />;
}

export function ChatListItem({ entry, now, onOpen, onOpenMenu }: ChatListItemProps) {
  const theme = useTheme();
  const preferences = usePreferences();
  const longPressAtRef = useRef(0);
  const isUnread = entry.unread > 0;
  const isEmphasized = isUnread || entry.incoming;
  const preview = describeChatPreview({
    preview: entry.preview,
    pending: entry.pending,
    typing: entry.typing,
    lastSeen: entry.lastSeen,
    now,
    onlineStatus: preferences.onlineStatus,
    lastSeenEnabled: preferences.lastSeen,
  });
  const previewColor = preview.accent ? theme.accent : isEmphasized ? theme.text : theme.placeholder;
  const time = formatConversationTime(entry.timestamp);

  const handlePress = () => {
    if (Date.now() - longPressAtRef.current < 800) return;
    onOpen(entry);
  };

  const handleLongPress = () => {
    longPressAtRef.current = Date.now();
    onOpenMenu(entry);
  };

  return (
    <NativeRipplePressable
      accessibilityHint="Mantén pulsado para abrir las opciones de la conversación"
      accessibilityLabel={`${entry.title}${entry.pinned ? ', fijada' : ''}${entry.muted ? ', silenciada' : ''}${entry.favorite ? ', favorita' : ''}${entry.typing ? ', escribiendo' : ''}${isUnread ? `, ${entry.unread} mensajes sin leer` : ''}, ${getTrustLabel(entry.vouchScore)}`}
      accessibilityRole="button"
      delayLongPress={350}
      onLongPress={handleLongPress}
      onPress={handlePress}
      style={[styles.item, { borderBottomColor: theme.border }]}
    >
      <XStack alignItems="center" gap={12} minHeight={80} paddingVertical={0}>
        <ContactTrustAvatar name={entry.title} size={55} vouchScore={entry.vouchScore} />
        <YStack flex={1} gap={4}>
          <XStack alignItems="center" gap={8}>
            {entry.favorite ? <Star color={theme.placeholder} size={13} strokeWidth={2.2} /> : null}
            {entry.pinned ? <Pin color={theme.placeholder} size={13} strokeWidth={2.2} /> : null}
            <SizableText color={theme.text} flex={1} fontSize={15} fontWeight={isEmphasized ? '700' : '600'} numberOfLines={1}>{entry.title}</SizableText>
            {entry.muted ? <BellOff accessibilityLabel="Silenciada" color={theme.placeholder} size={13} strokeWidth={2.2} /> : null}
            {!!time && <Text color={entry.typing || isEmphasized ? theme.accent : theme.placeholder} fontSize={11} fontWeight={entry.typing || isEmphasized ? '700' : '500'}>{time}</Text>}
          </XStack>
          <XStack alignItems="center" gap={5}>
            <LastMessageStatus color={theme.placeholder} entry={entry} />
            <Text color={entry.incoming ? theme.accent : previewColor} flex={1} fontSize={13} fontStyle={preview.italic ? 'italic' : 'normal'} numberOfLines={1}>{preview.text}</Text>
            {isUnread && (
              <XStack alignItems="center" backgroundColor={theme.accent} borderRadius={10} height={20} justifyContent="center" minWidth={20} paddingHorizontal={6}>
                <SizableText color={theme.onAccent} fontSize={11} fontWeight="700">{entry.unread > 99 ? '99+' : entry.unread}</SizableText>
              </XStack>
            )}
          </XStack>
        </YStack>
      </XStack>
    </NativeRipplePressable>
  );
}

const styles = StyleSheet.create({
  item: { borderBottomWidth: StyleSheet.hairlineWidth, marginHorizontal: -12, paddingHorizontal: 12 },
});
