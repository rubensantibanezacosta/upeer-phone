import { useCallback, useEffect, useRef, useState } from 'react';
import { Keyboard, StyleSheet, type StyleProp, type ViewStyle } from 'react-native';
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

function ComposerAction({ label, onPress, style, children }: { children: React.ReactNode; label: string; onPress: () => void; style?: StyleProp<ViewStyle> }) {
  return (
    <NativeRipplePressable accessibilityLabel={label} accessibilityRole="button" onPress={onPress} style={[styles.action, style]}>
      {children}
    </NativeRipplePressable>
  );
}

export function ChatComposer({ value, onChange, onSend, onAttach, onUnavailable }: ChatComposerProps) {
  const theme = useTheme();
  const [emojiOpen, setEmojiOpen] = useState(false);
  const [forcedSelection, setForcedSelection] = useState<{ end: number; start: number } | null>(null);
  const selection = useRef({ end: value.length, start: value.length });
  const text = useRef(value);
  const hasText = value.trim().length > 0;

  useEffect(() => {
    text.current = value;
  }, [value]);

  const handleSelection = useCallback((event: { nativeEvent: { selection: { end: number; start: number } } }) => {
    selection.current = event.nativeEvent.selection;
    setForcedSelection((current) => (current === null ? current : null));
  }, []);

  const handleChangeText = useCallback((next: string) => {
    text.current = next;
    onChange(next);
  }, [onChange]);

  const insertEmoji = useCallback((emoji: string) => {
    const next = insertAtSelection(text.current, selection.current, emoji);
    text.current = next.value;
    selection.current = next.selection;
    setForcedSelection(next.selection);
    onChange(next.value);
  }, [onChange]);

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
      <ComposerAction label="Abrir emojis" onPress={toggleEmoji} style={styles.emojiAction}><Smile color={emojiOpen ? theme.accent : theme.textSecondary} size={22} /></ComposerAction>
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
        onChangeText={handleChangeText}
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
  emojiAction: { marginLeft: -14 },
  input: { paddingHorizontal: 8, paddingVertical: 8, textAlignVertical: 'center' },
});
