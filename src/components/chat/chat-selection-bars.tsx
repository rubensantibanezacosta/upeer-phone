import type { ReactNode } from 'react';
import { StyleSheet, View } from 'react-native';
import { Copy, Forward, Star, Trash2, X } from '@tamagui/lucide-icons-2';
import { SizableText } from 'tamagui';

import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { describeSelection } from '@/domain/chat-selection';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

type ChatSelectionBarProps = {
  count: number;
  onClose: () => void;
};

export function ChatSelectionBar({ count, onClose }: ChatSelectionBarProps) {
  const theme = useTheme();
  const scale = useTextScale();

  return (
    <View style={[styles.bar, { backgroundColor: theme.background, borderColor: theme.border }]}>
      <NativeRipplePressable accessibilityLabel="Salir de la selección" accessibilityRole="button" onPress={onClose} style={styles.button}>
        <X color={theme.text} size={22} />
      </NativeRipplePressable>
      <SizableText color={theme.text} flex={1} fontSize={scale(16)} fontWeight="700">{describeSelection(count)}</SizableText>
    </View>
  );
}

type ChatSelectionToolbarProps = {
  selection: {
    chosen: readonly unknown[];
    copySelected: () => void;
    deletableCount: number;
    deleteSelected: () => Promise<void>;
    forwardSelected: () => void;
    forwardableCount: number;
    allStarredSelected: boolean;
    starrableCount: number;
    toggleStarSelected: () => void;
  };
};

export function ChatSelectionToolbar({ selection }: ChatSelectionToolbarProps) {
  const theme = useTheme();
  const scale = useTextScale();
  const canCopy = selection.chosen.length > 0;
  const canDelete = selection.deletableCount > 0;
  const canForward = selection.forwardableCount > 0;
  const canStar = selection.starrableCount > 0;
  const action = (label: string, icon: ReactNode, enabled: boolean, onPress: () => void, tone: string = theme.text) => (
    <NativeRipplePressable accessibilityLabel={label} accessibilityRole="button" accessibilityState={{ disabled: !enabled }} disabled={!enabled} onPress={onPress} style={[styles.action, !enabled ? styles.disabled : null]}>
      {icon}
      <SizableText color={tone} fontSize={scale(11)}>{label}</SizableText>
    </NativeRipplePressable>
  );

  return (
    <View style={[styles.toolbar, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
      {action('Reenviar', <Forward color={canForward ? theme.text : theme.placeholder} size={20} />, canForward, selection.forwardSelected)}
      {action('Copiar', <Copy color={canCopy ? theme.text : theme.placeholder} size={20} />, canCopy, selection.copySelected)}
      {action(selection.allStarredSelected ? 'Quitar' : 'Destacar', <Star color={canStar ? theme.accent : theme.placeholder} fill={selection.allStarredSelected ? theme.accent : 'none'} size={20} />, canStar, selection.toggleStarSelected)}
      {action('Eliminar', <Trash2 color={canDelete ? theme.danger : theme.placeholder} size={20} />, canDelete, () => { void selection.deleteSelected(); }, theme.danger)}
    </View>
  );
}

const styles = StyleSheet.create({
  action: { alignItems: 'center', flex: 1, gap: 3, justifyContent: 'center', paddingVertical: 10 },
  bar: { alignItems: 'center', borderBottomWidth: StyleSheet.hairlineWidth, flexDirection: 'row', gap: 8, minHeight: 56, paddingHorizontal: 8 },
  button: { alignItems: 'center', borderRadius: 22, height: 44, justifyContent: 'center', width: 44 },
  disabled: { opacity: 0.5 },
  toolbar: { alignItems: 'center', borderTopWidth: StyleSheet.hairlineWidth, flexDirection: 'row', paddingHorizontal: 8 },
});
