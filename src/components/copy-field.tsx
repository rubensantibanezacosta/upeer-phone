import { StyleSheet, Text } from 'react-native';
import { Copy } from '@tamagui/lucide-icons-2';

import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { useTheme } from '@/hooks/use-theme';

export function CopyField({ value, onCopy, accessibilityLabel }: {
  value: string;
  onCopy: () => void;
  accessibilityLabel: string;
}) {
  const theme = useTheme();
  return (
    <NativeRipplePressable accessibilityLabel={accessibilityLabel} accessibilityRole="button" accessibilityState={{ disabled: !value }} disabled={!value} onPress={onCopy} style={[styles.field, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
      <Text style={[styles.value, { color: theme.textSecondary }]}>{value || '—'}</Text>
      <Copy color={theme.placeholder} size={16} />
    </NativeRipplePressable>
  );
}

const styles = StyleSheet.create({ field: { alignItems: 'center', borderRadius: 8, borderWidth: 1, flexDirection: 'row', gap: 8, minHeight: 44, paddingHorizontal: 12, paddingVertical: 8 }, value: { flex: 1, flexShrink: 1, fontFamily: 'monospace', fontSize: 12, lineHeight: 19 } });
