import { useCallback, useRef, useState } from 'react';
import { Keyboard, StyleSheet } from 'react-native';
import { Mic, Plus, Send, Smile } from '@tamagui/lucide-icons-2';
import { Input, XStack } from 'tamagui';

import { EmojiPanel } from '@/components/chat/emoji-panel';
import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { insertAtSelection } from '@/domain/composer-text';
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
  const [emojiOpen, setEmojiOpen] = useState(false);
  const [forcedSelection, setForcedSelection] = useState<{ end: number; start: number } | null>(null);
  const selection = useRef({ end: value.length, start: value.length });
  const hasText = value.trim().length > 0;

  const handleSelection = useCallback((event: { nativeEvent: { selection: { end: number; start: number } } }) => {
    selection.current = event.nativeEvent.selection;
    setForcedSelection(null);
  }, []);

  const insertEmoji = useCallback((emoji: string) => {
    const next = insertAtSelection(value, selection.current, emoji);
    selection.current = next.selection;
    setForcedSelection(next.selection);
    onChange(next.value);
  }, [onChange, value]);

  const toggleEmoji = () => {
    setEmojiOpen((open) => {
      if (open) return false;
      Keyboard.dismiss();
      return true;
    });
  };

  return <>
    <XStack alignItems="flex-end" backgroundColor={theme.backgroundSelected} borderRadius={24} gap={2} marginHorizontal={8} marginVertical={8} paddingHorizontal={4} paddingVertical={4}>
      <ComposerAction label="Adjuntar archivo" onPress={onAttach}><Plus color={theme.textSecondary} size={22} /></ComposerAction>
      <ComposerAction label="Abrir emojis" onPress={toggleEmoji}><Smile color={emojiOpen ? theme.accent : theme.textSecondary} size={22} /></ComposerAction>
      <Input
        accessibilityLabel="Mensaje"
        backgroundColor="transparent"
        borderWidth={0}
        color={theme.text}
        flex={1}
        maxHeight={128}
        maxLength={4000}
        minHeight={44}
        multiline
        onChangeText={onChange}
        onFocus={() => setEmojiOpen(false)}
        onSelectionChange={handleSelection}
        placeholder="Escribe un mensaje..."
        placeholderTextColor={theme.placeholder}
        selection={forcedSelection ?? undefined}
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
    {emojiOpen ? <EmojiPanel onPick={insertEmoji} /> : null}
  </>;
}

const styles = StyleSheet.create({
  action: { alignItems: 'center', borderRadius: 22, height: 44, justifyContent: 'center', overflow: 'hidden', width: 44 },
  input: { paddingHorizontal: 8, paddingVertical: 8, textAlignVertical: 'center' },
});
