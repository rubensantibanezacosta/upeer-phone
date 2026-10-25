import { StyleSheet, View } from 'react-native';
import { Phone, Video } from '@tamagui/lucide-icons-2';
import { Text } from 'tamagui';

import { callEventLabel, callEventOutcome, type SystemMessage } from '@/domain/system-message';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

function startTimeLabel(startedAt: number | null): string | null {
  if (startedAt === null) return null;
  return new Date(startedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

export function ChatSystemMessage({ message }: { message: SystemMessage }) {
  const theme = useTheme();
  const scale = useTextScale();
  const { call } = message;

  if (call) {
    const Icon = call.kind === 'video' ? Video : Phone;
    const startedAt = startTimeLabel(call.startedAt);
    return (
      <View accessibilityLabel={`${callEventLabel(call)}${startedAt ? `, iniciada a las ${startedAt}` : ''}, ${callEventOutcome(call)}`} style={styles.row}>
        <View style={[styles.card, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
          <View style={[styles.badge, { backgroundColor: theme.successSoft }]}>
            <Icon color={theme.success} size={18} strokeWidth={2} />
          </View>
          <View style={styles.texts}>
            <Text color={theme.text} fontSize={scale(13)} fontWeight="600">{callEventLabel(call)}</Text>
            {startedAt ? <Text color={theme.placeholder} fontSize={scale(11)}>Inicio {startedAt}</Text> : null}
            <Text color={call.duration ? theme.placeholder : theme.danger} fontSize={scale(11)}>{callEventOutcome(call)}</Text>
          </View>
        </View>
      </View>
    );
  }

  return (
    <View style={styles.row}>
      <Text color={theme.placeholder} fontSize={scale(11)} fontStyle="italic" style={[styles.pill, { backgroundColor: theme.backgroundSelected }]}>{message.text}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  badge: { alignItems: 'center', borderRadius: 16, height: 32, justifyContent: 'center', width: 32 },
  card: { alignItems: 'center', borderRadius: 12, borderWidth: StyleSheet.hairlineWidth, flexDirection: 'row', gap: 8, minWidth: 190, paddingHorizontal: 12, paddingVertical: 8 },
  pill: { borderRadius: 20, overflow: 'hidden', paddingHorizontal: 12, paddingVertical: 3, textAlign: 'center' },
  row: { alignItems: 'center', paddingHorizontal: 24, width: '100%' },
  texts: { gap: 1 },
});
