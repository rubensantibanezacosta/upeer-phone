import { StyleSheet } from 'react-native';
import { Mic, Plus, Send } from '@tamagui/lucide-icons-2';
import { Input, XStack } from 'tamagui';

import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { useTheme } from '@/hooks/use-theme';

type ChatComposerProps = {
  value: string;
  onChange: (value: string) => void;
  onSend: () => void;
  onAttach: () => void;
  onUnavailable: (label: string) => void;
};

function ComposerAction({ label, onPress, children }: { label: string; onPress: () => void; children: React.ReactNode }) {
  return (
    <NativeRipplePressable accessibilityLabel={label} accessibilityRole="button" onPress={onPress} style={styles.action}>
      {children}
    </NativeRipplePressable>
  );
}

export function ChatComposer({ value, onChange, onSend, onAttach, onUnavailable }: ChatComposerProps) {
  const theme = useTheme();
  const hasText = value.trim().length > 0;
  return (
    <XStack alignItems="flex-end" backgroundColor={theme.backgroundSelected} borderRadius={24} gap={2} marginHorizontal={8} marginVertical={8} paddingHorizontal={4} paddingVertical={4}>
      <ComposerAction label="Adjuntar archivo" onPress={onAttach}><Plus color={theme.textSecondary} size={22} /></ComposerAction>
      <Input
        accessibilityLabel="Mensaje"
        backgroundColor="transparent"
        borderWidth={0}
        color={theme.text}
        flex={1}
        maxHeight={128}
        maxLength={4000}
        minHeight={40}
        multiline
        onChangeText={onChange}
        placeholder="Escribe un mensaje..."
        placeholderTextColor={theme.placeholder}
        style={styles.input}
        value={value}
      />
      <NativeRipplePressable
        accessibilityLabel={hasText ? 'Enviar mensaje' : 'Grabar nota de voz'}
        accessibilityRole="button"
        onPress={hasText ? onSend : () => onUnavailable('Grabar nota de voz')}
        style={styles.action}
      >
        {hasText ? <Send color={theme.accent} size={22} /> : <Mic color={theme.accent} size={22} />}
      </NativeRipplePressable>
    </XStack>
  );
}

const styles = StyleSheet.create({
  action: { alignItems: 'center', borderRadius: 20, height: 40, justifyContent: 'center', overflow: 'hidden', width: 40 },
  input: { paddingHorizontal: 8, paddingVertical: 8, textAlignVertical: 'center' },
});
