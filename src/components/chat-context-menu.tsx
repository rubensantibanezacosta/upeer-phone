import type { ReactNode } from 'react';
import { Modal, Pressable, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Archive, BellOff, CheckCheck, Pin, Star, Trash2, X } from '@tamagui/lucide-icons-2';
import { SizableText, Text, XStack, YStack } from 'tamagui';

import type { ChatListEntry } from '@/components/chat-list-item';
import type { ConversationFlag } from '@/domain/conversation-flags';
import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { ProfileAvatar } from '@/components/profile-avatar';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

type ChatContextMenuProps = {
  entry: ChatListEntry | null;
  onClose: () => void;
  onClear: (entry: ChatListEntry) => void;
  onMarkRead: (entry: ChatListEntry) => void;
  onToggleFavorite: (entry: ChatListEntry) => void;
  onToggleFlag: (entry: ChatListEntry, flag: ConversationFlag) => void;
};

type MenuActionProps = {
  color: string;
  disabled?: boolean;
  icon: ReactNode;
  label: string;
  onPress?: () => void;
};

function MenuAction({ color, disabled = false, icon, label, onPress }: MenuActionProps) {
  const scale = useTextScale();
  return (
    <NativeRipplePressable accessibilityRole="button" accessibilityState={{ disabled }} disabled={disabled} onPress={onPress} style={[styles.action, disabled && styles.disabledAction]}>
      {icon}
      <Text color={color} fontSize={scale(14)} fontWeight="600">{label}</Text>
    </NativeRipplePressable>
  );
}

export function ChatContextMenu({ entry, onClear, onClose, onMarkRead, onToggleFavorite, onToggleFlag }: ChatContextMenuProps) {
  const theme = useTheme();
  const scale = useTextScale();
  const insets = useSafeAreaInsets();
  if (!entry) return null;
  const canMarkRead = entry.unread > 0;

  return (
    <Modal animationType="fade" onRequestClose={onClose} presentationStyle="overFullScreen" statusBarTranslucent transparent visible>
      <View style={styles.overlay}>
        <Pressable accessibilityLabel="Cerrar menú de conversación" onPress={onClose} style={[StyleSheet.absoluteFill, { backgroundColor: theme.backdrop }]} />
        <View accessibilityViewIsModal style={[styles.menu, { backgroundColor: theme.backgroundElement, borderColor: theme.border, paddingBottom: Math.max(insets.bottom, 12) }]}>
          <XStack alignItems="center" gap={12} paddingHorizontal={16} paddingVertical={14}>
            <ProfileAvatar name={entry.title} size={44} />
            <YStack flex={1} gap={2}>
              <SizableText color={theme.text} fontSize={scale(16)} fontWeight="700" numberOfLines={1}>{entry.title}</SizableText>
              <Text color={theme.placeholder} fontSize={scale(11)} numberOfLines={1} style={styles.identifier}>{entry.upeerId}</Text>
            </YStack>
            <NativeRipplePressable accessibilityLabel="Cerrar menú" onPress={onClose} style={styles.closeButton}><X color={theme.placeholder} size={18} /></NativeRipplePressable>
          </XStack>
          <View style={[styles.divider, { backgroundColor: theme.border }]} />
          <MenuAction color={theme.text} icon={<Archive color={theme.text} size={19} />} label={entry.archived ? 'Desarchivar chat' : 'Archivar chat'} onPress={() => onToggleFlag(entry, 'archived')} />
          <MenuAction color={theme.text} icon={<BellOff color={theme.text} size={19} />} label={entry.muted ? 'Activar notificaciones' : 'Silenciar notificaciones'} onPress={() => onToggleFlag(entry, 'muted')} />
          <MenuAction color={theme.text} icon={<Pin color={theme.text} size={19} />} label={entry.pinned ? 'Desfijar chat' : 'Fijar chat'} onPress={() => onToggleFlag(entry, 'pinned')} />
          <View style={[styles.divider, { backgroundColor: theme.border }]} />
          <MenuAction
            color={canMarkRead ? theme.text : theme.placeholder}
            disabled={!canMarkRead}
            icon={<CheckCheck color={canMarkRead ? theme.accent : theme.placeholder} size={19} />}
            label="Marcar como leído"
            onPress={() => onMarkRead(entry)}
          />
          <MenuAction color={theme.text} icon={<Star color={theme.text} size={19} />} label={entry.favorite ? 'Quitar de Favoritos' : 'Añadir a Favoritos'} onPress={() => onToggleFavorite(entry)} />
          <View style={[styles.divider, { backgroundColor: theme.border }]} />
          <MenuAction color={theme.danger} icon={<Trash2 color={theme.danger} size={19} />} label="Vaciar chat" onPress={() => onClear(entry)} />
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: { flex: 1, justifyContent: 'flex-end', paddingHorizontal: 12 },
  menu: { borderRadius: 18, borderWidth: StyleSheet.hairlineWidth, elevation: 14, overflow: 'hidden', shadowColor: '#000', shadowOffset: { height: 8, width: 0 }, shadowOpacity: 0.32, shadowRadius: 18 },
  action: { alignItems: 'center', flexDirection: 'row', gap: 14, minHeight: 52, paddingHorizontal: 18 },
  disabledAction: { opacity: 0.55 },
  closeButton: { alignItems: 'center', borderRadius: 20, height: 40, justifyContent: 'center', width: 40 },
  divider: { height: StyleSheet.hairlineWidth, marginHorizontal: 16 },
  identifier: { fontFamily: 'monospace' },
});
