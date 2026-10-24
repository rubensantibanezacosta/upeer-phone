import type { ReactNode } from 'react';
import { Modal, Pressable, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Copy, Forward, Pencil, Reply, RotateCcw, Trash2, X } from '@tamagui/lucide-icons-2';
import { SizableText, Text, XStack } from 'tamagui';

import type { ProtocolMessage } from '../../../modules/upeer-transport/src/UPeerTransport.types';
import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { QUICK_REACTIONS, type ReactionSummary } from '@/domain/chat-reactions';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

type ChatMessageActionsProps = {
  message: ProtocolMessage | null;
  reactions: ReactionSummary[];
  canEdit: boolean;
  canRetry: boolean;
  onClose: () => void;
  onCopy: (message: ProtocolMessage) => void;
  onDelete: (message: ProtocolMessage) => void;
  onEdit: (message: ProtocolMessage) => void;
  onForward: (message: ProtocolMessage) => void;
  onReply: (message: ProtocolMessage) => void;
  onRetry: (message: ProtocolMessage) => void;
  onToggleReaction: (message: ProtocolMessage, emoji: string) => void;
};

type ActionRowProps = {
  color: string;
  icon: ReactNode;
  label: string;
  onPress: () => void;
};

function ActionRow({ color, icon, label, onPress }: ActionRowProps) {
  const scale = useTextScale();
  return (
    <NativeRipplePressable accessibilityRole="button" onPress={onPress} style={styles.action}>
      {icon}
      <Text color={color} fontSize={scale(14)} fontWeight="600">{label}</Text>
    </NativeRipplePressable>
  );
}

export function ChatMessageActions({ message, reactions, canEdit, canRetry, onClose, onCopy, onDelete, onEdit, onForward, onReply, onRetry, onToggleReaction }: ChatMessageActionsProps) {
  const theme = useTheme();
  const scale = useTextScale();
  const insets = useSafeAreaInsets();
  if (!message) return null;

  return (
    <Modal animationType="fade" onRequestClose={onClose} presentationStyle="overFullScreen" statusBarTranslucent transparent visible>
      <View style={styles.overlay}>
        <Pressable accessibilityLabel="Cerrar acciones del mensaje" onPress={onClose} style={[StyleSheet.absoluteFill, { backgroundColor: theme.backdrop }]} />
        <View accessibilityViewIsModal style={[styles.menu, { backgroundColor: theme.backgroundElement, borderColor: theme.border, paddingBottom: Math.max(insets.bottom, 12) }]}>
          <XStack alignItems="center" gap={12} paddingHorizontal={16} paddingVertical={14}>
            <SizableText color={theme.text} flex={1} fontSize={scale(16)} fontWeight="700" numberOfLines={1}>Mensaje</SizableText>
            <NativeRipplePressable accessibilityLabel="Cerrar menú" onPress={onClose} style={styles.closeButton}><X color={theme.placeholder} size={18} /></NativeRipplePressable>
          </XStack>
          <View style={[styles.divider, { backgroundColor: theme.border }]} />
          <XStack gap={8} justifyContent="space-between" paddingHorizontal={16} paddingVertical={12}>
            {QUICK_REACTIONS.map((emoji) => {
              const active = reactions.some((reaction) => reaction.emoji === emoji && reaction.mine);
              return (
                <NativeRipplePressable
                  accessibilityLabel={`Reaccionar con ${emoji}`}
                  accessibilityRole="button"
                  accessibilityState={{ selected: active }}
                  key={emoji}
                  onPress={() => onToggleReaction(message, emoji)}
                  style={[styles.emoji, { backgroundColor: active ? theme.accent : theme.background, borderColor: active ? theme.accent : theme.border }]}
                >
                  <Text fontSize={20}>{emoji}</Text>
                </NativeRipplePressable>
              );
            })}
          </XStack>
          <View style={[styles.divider, { backgroundColor: theme.border }]} />
          {canRetry ? <ActionRow color={theme.danger} icon={<RotateCcw color={theme.danger} size={19} />} label="Reintentar envío" onPress={() => onRetry(message)} /> : null}
          {canEdit ? <ActionRow color={theme.text} icon={<Pencil color={theme.text} size={19} />} label="Editar mensaje" onPress={() => onEdit(message)} /> : null}
          <ActionRow color={theme.text} icon={<Reply color={theme.text} size={19} />} label="Responder" onPress={() => onReply(message)} />
          <ActionRow color={theme.text} icon={<Forward color={theme.text} size={19} />} label="Reenviar" onPress={() => onForward(message)} />
          <ActionRow color={theme.text} icon={<Copy color={theme.text} size={19} />} label="Copiar texto" onPress={() => onCopy(message)} />
          <View style={[styles.divider, { backgroundColor: theme.border }]} />
          <ActionRow color={theme.danger} icon={<Trash2 color={theme.danger} size={19} />} label="Eliminar para todos" onPress={() => onDelete(message)} />
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: { flex: 1, justifyContent: 'flex-end', paddingHorizontal: 12 },
  menu: { borderRadius: 18, borderWidth: StyleSheet.hairlineWidth, elevation: 14, overflow: 'hidden', shadowColor: '#000', shadowOffset: { height: 8, width: 0 }, shadowOpacity: 0.32, shadowRadius: 18 },
  action: { alignItems: 'center', flexDirection: 'row', gap: 14, minHeight: 52, paddingHorizontal: 18 },
  emoji: { alignItems: 'center', borderRadius: 8, borderWidth: StyleSheet.hairlineWidth, flex: 1, height: 44, justifyContent: 'center' },
  closeButton: { alignItems: 'center', borderRadius: 20, height: 40, justifyContent: 'center', width: 40 },
  divider: { height: StyleSheet.hairlineWidth, marginHorizontal: 16 },
});
