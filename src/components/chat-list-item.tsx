import { useRef } from 'react';
import { StyleSheet, View } from 'react-native';
import { BellOff, Check, CheckCheck, CircleAlert, Clock3, FileImage, FileText, FileVideo, Mic, Pin, Star, Users } from '@tamagui/lucide-icons-2';
import { SizableText, Text, XStack, YStack } from 'tamagui';

import { ContactTrustAvatar } from '@/components/contact-trust-avatar';
import { UnreadCountBadge } from '@/components/unread-count-badge';
import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import type { AttachmentKind } from '@/domain/attachment';
import { describeChatPreview, type ConversationActivity } from '@/domain/chat-preview';
import { statusIconIsRead, statusIconKind } from '@/domain/message-status-icon';
import { getTrustAnnouncement } from '@/domain/trust';
import type { ProtocolMessage } from '../../modules/upeer-transport/src/UPeerTransport.types';
import { usePreferences } from '@/hooks/use-preferences';
import { useVouchScore } from '@/hooks/use-vouch-score';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

export type ChatListEntry = {
  upeerId: string;
  title: string;
  avatar?: string;
  preview: string;
  timestamp: number;
  unread: number;
  isMine: boolean;
  lastStatus: ProtocolMessage['status'] | null;
  previewIcon?: AttachmentKind | null;
  reaction?: boolean;
  pending?: boolean;
  incoming?: boolean;
  vouchScore?: number;
  archived?: boolean;
  muted?: boolean;
  pinned?: boolean;
  favorite?: boolean;
  activity?: ConversationActivity | '';
  lastActive?: number;
  group?: boolean;
  members?: number;
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
  if (entry.reaction || !entry.isMine || !entry.lastStatus) return null;
  const kind = statusIconKind(entry.lastStatus);
  if (kind === 'failed') return <CircleAlert color={theme.danger} size={15} strokeWidth={2.2} />;
  if (kind === 'pending') return <Clock3 color={color} size={14} strokeWidth={2.2} />;
  if (kind === 'sent') return <Check color={color} size={15} strokeWidth={2.2} />;
  return <CheckCheck color={statusIconIsRead(entry.lastStatus) ? theme.accent : color} size={15} strokeWidth={2.2} />;
}

function PreviewAttachmentIcon({ kind, color }: { kind: ChatListEntry['previewIcon']; color: string }) {
  if (!kind) return null;
  const Icon = kind === 'image' ? FileImage : kind === 'video' ? FileVideo : kind === 'voice' ? Mic : FileText;
  return <Icon color={color} size={14} strokeWidth={2.2} />;
}

export function ChatListItem({ entry, now, onOpen, onOpenMenu }: ChatListItemProps) {
  const theme = useTheme();
  const preferences = usePreferences();
  const scale = useTextScale();
  const longPressAtRef = useRef(0);
  const vouchScore = useVouchScore(entry.upeerId) ?? entry.vouchScore;
  const isUnread = entry.unread > 0;
  const isEmphasized = isUnread || entry.incoming;
  const preview = describeChatPreview({
    preview: entry.preview,
    pending: entry.pending,
    activity: entry.activity,
    lastActive: entry.lastActive,
    now,
    shareActivity: preferences.shareActivity,
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
      accessibilityLabel={`${entry.title}${entry.pinned ? ', fijada' : ''}${entry.muted ? ', silenciada' : ''}${entry.favorite ? ', favorita' : ''}${entry.activity === 'recording' ? ', grabando audio' : entry.activity ? ', escribiendo' : ''}${isUnread ? `, ${entry.unread} mensajes sin leer` : ''}${getTrustAnnouncement(vouchScore) ? `, ${getTrustAnnouncement(vouchScore)}` : ''}`}
      accessibilityRole="button"
      delayLongPress={350}
      onLongPress={handleLongPress}
      onPress={handlePress}
      style={[styles.item, { borderBottomColor: theme.border }]}
    >
      <XStack alignItems="center" gap={12} minHeight={80} paddingVertical={0}>
        {entry.group ? <View style={[styles.groupAvatar, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}><Users color={theme.placeholder} size={24} /></View> : <ContactTrustAvatar avatar={entry.avatar} name={entry.title} size={55} vouchScore={vouchScore} />}
        <YStack flex={1} gap={4}>
          <XStack alignItems="center" gap={8}>
            {entry.favorite ? <Star color={theme.placeholder} size={13} strokeWidth={2.2} /> : null}
            {entry.pinned ? <Pin color={theme.placeholder} size={13} strokeWidth={2.2} /> : null}
            <SizableText color={theme.text} flex={1} fontSize={scale(15)} fontWeight={isEmphasized ? '700' : '600'} numberOfLines={1}>{entry.title}</SizableText>
            {entry.muted ? <BellOff accessibilityLabel="Silenciada" color={theme.placeholder} size={13} strokeWidth={2.2} /> : null}
            {!!time && <Text color={entry.activity || isEmphasized ? theme.accent : theme.placeholder} fontSize={scale(11)} fontWeight={entry.activity || isEmphasized ? '700' : '500'}>{time}</Text>}
          </XStack>
          <XStack alignItems="center" gap={5}>
            <LastMessageStatus color={theme.placeholder} entry={entry} />
            {preview.accent ? null : <PreviewAttachmentIcon color={previewColor} kind={entry.previewIcon} />}
            <Text color={entry.incoming ? theme.accent : previewColor} flex={1} fontSize={scale(13)} fontStyle={preview.italic ? 'italic' : 'normal'} numberOfLines={1}>{preview.text}</Text>
            {isUnread ? <UnreadCountBadge count={entry.unread} /> : null}
          </XStack>
        </YStack>
      </XStack>
    </NativeRipplePressable>
  );
}

const styles = StyleSheet.create({
  groupAvatar: { alignItems: 'center', borderRadius: 27.5, borderWidth: StyleSheet.hairlineWidth, height: 55, justifyContent: 'center', width: 55 },
  item: { borderBottomWidth: StyleSheet.hairlineWidth, marginHorizontal: -12, paddingHorizontal: 12 },
});
