import { StyleSheet, View } from 'react-native';
import { Text } from 'tamagui';

import { formatChatDateLabel } from '@/domain/chat-timeline';
import { useTheme } from '@/hooks/use-theme';

export function ChatDateSeparator({ timestamp }: { timestamp: number }) {
  const theme = useTheme();
  const label = formatChatDateLabel(timestamp);
  if (!label) return null;
  return (
    <View accessibilityLabel={`Fecha de los mensajes: ${label}`} style={styles.root}>
      <View style={[styles.label, { backgroundColor: theme.backgroundElement }]}>
        <Text color={theme.textSecondary} style={styles.text}>{label}</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { alignItems: 'center', justifyContent: 'center', marginBottom: 4, marginTop: 8, minHeight: 24 },
  label: { borderRadius: 12, paddingHorizontal: 16, paddingVertical: 4 },
  text: { fontSize: 11, fontWeight: '600', letterSpacing: 0.55, textTransform: 'uppercase' },
});
