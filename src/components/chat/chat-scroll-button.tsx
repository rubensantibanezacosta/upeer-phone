import { StyleSheet } from 'react-native';
import { ArrowDown } from '@tamagui/lucide-icons-2';

import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { useTheme } from '@/hooks/use-theme';

export function ChatScrollButton({ onPress }: { onPress: () => void }) {
  const theme = useTheme();
  return (
    <NativeRipplePressable
      accessibilityLabel="Ir a los mensajes más recientes"
      accessibilityRole="button"
      onPress={onPress}
      style={[styles.button, { backgroundColor: theme.backgroundElement }]}
    >
      <ArrowDown color={theme.text} size={24} />
    </NativeRipplePressable>
  );
}

const styles = StyleSheet.create({
  button: { alignItems: 'center', borderRadius: 8, bottom: 14, elevation: 6, height: 40, justifyContent: 'center', position: 'absolute', right: 16, shadowColor: '#000', shadowOffset: { height: 2, width: 0 }, shadowOpacity: 0.3, shadowRadius: 8, width: 40 },
});
