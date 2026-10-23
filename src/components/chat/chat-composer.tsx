import { StyleSheet } from 'react-native';
import { Mic, Plus, Send } from '@tamagui/lucide-icons-2';
import { Input, XStack } from 'tamagui';

import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { useTheme } from '@/hooks/use-theme';

type ChatComposerProps = {
  value: string;
  onChange: (value: string) => void;
  onSend: () => void;
  onUnavailable: (label: string) => void;
};

function ComposerAction({ label, onPress, children }: { label: string; onPress: () => void; children: React.ReactNode }) {
  return (
    <NativeRipplePressable accessibilityLabel={label} accessibilityRole="button" onPress={onPress} style={styles.action}>
      {children}
    </NativeRipplePressable>
  );
}

export function ChatComposer({ value, onChange, onSend, onUnavailable }: ChatComposerProps) {
  const theme = useTheme();
  const hasText = value.trim().length > 0;
  return (
    <XStack alignItems="flex-end" backgroundColor={theme.backgroundElement} borderTopColor={theme.border} borderTopWidth={StyleSheet.hairlineWidth} gap={6} paddingHorizontal={8} paddingVertical={8}>
      <ComposerAction label="Adjuntar archivo" onPress={() => onUnavailable('Adjuntar archivo')}><Plus color={theme.textSecondary} size={24} /></ComposerAction>
      <Input
        accessibilityLabel="Mensaje"
        backgroundColor={theme.backgroundSelected}
        borderWidth={0}
        borderRadius={8}
        color={theme.text}
        flex={1}
        maxHeight={128}
        maxLength={4000}
        minHeight={48}
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
        style={styles.send}
      >
        {hasText ? <Send color={theme.accent} size={24} /> : <Mic color={theme.accent} size={24} />}
      </NativeRipplePressable>
    </XStack>
  );
}

const styles = StyleSheet.create({
  action: { alignItems: 'center', borderRadius: 8, height: 48, justifyContent: 'center', overflow: 'hidden', width: 48 },
  input: { paddingHorizontal: 16, paddingVertical: 12, textAlignVertical: 'center' },
  send: { alignItems: 'center', borderRadius: 8, height: 48, justifyContent: 'center', width: 48 },
});
