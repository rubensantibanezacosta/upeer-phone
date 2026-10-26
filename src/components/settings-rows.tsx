import { StyleSheet, View } from 'react-native';
import { SizableText, Text, YStack } from 'tamagui';

import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

type SettingsRowProps = {
  label: string;
  description: string;
};

export function SettingsToggleRow({ label, description, value, onChange }: SettingsRowProps & { value: boolean; onChange: (value: boolean) => void }) {
  const theme = useTheme();
  const scale = useTextScale();
  return <NativeRipplePressable
    accessibilityLabel={label}
    accessibilityRole="switch"
    accessibilityState={{ checked: value }}
    onPress={() => onChange(!value)}
    style={styles.row}
  >
    <YStack flex={1} gap={2}>
      <SizableText color={theme.text} fontSize={scale(15)} fontWeight="500">{label}</SizableText>
      <Text color={theme.placeholder} fontSize={scale(12)}>{description}</Text>
    </YStack>
    <View style={[styles.track, { backgroundColor: value ? theme.accent : theme.backgroundSelected, borderColor: value ? theme.accent : theme.border }]}>
      <View style={[styles.thumb, value ? styles.thumbOn : styles.thumbOff, { backgroundColor: value ? theme.onAccent : theme.background }]} />
    </View>
  </NativeRipplePressable>;
}

export function SettingsInfoRow({ label, value }: { label: string; value: string }) {
  const theme = useTheme();
  const scale = useTextScale();
  return <View style={styles.row}>
    <SizableText color={theme.text} flex={1} fontSize={scale(15)} fontWeight="500">{label}</SizableText>
    <Text color={theme.placeholder} fontSize={scale(13)}>{value}</Text>
  </View>;
}

const styles = StyleSheet.create({
  row: { alignItems: 'center', flexDirection: 'row', gap: 12, minHeight: 64, paddingHorizontal: 16, paddingVertical: 12 },
  track: { borderRadius: 14, borderWidth: 1, height: 28, justifyContent: 'center', width: 48 },
  thumb: { borderRadius: 10, height: 20, position: 'absolute', width: 20 },
  thumbOff: { left: 3 },
  thumbOn: { left: 23 },
});
