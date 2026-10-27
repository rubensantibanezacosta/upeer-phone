import { useCallback, useEffect, useRef, useState } from 'react';
import { Keyboard, StyleSheet, View, type TextInputInstance } from 'react-native';
import { Camera, Mic, Paperclip, Send, Smile } from '@tamagui/lucide-icons-2';
import { Input, XStack } from 'tamagui';

import { EmojiPanel } from '@/components/chat/emoji-panel';
import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { deleteBeforeSelection, insertAtSelection, trackedSelection } from '@/domain/composer-text';
import { useTheme } from '@/hooks/use-theme';

const INPUT_MIN_HEIGHT = 44;
const PILL_PADDING_X = 6;
const PILL_PADDING_Y = 4;
const ICON_SIZE = 24;
const ACTION_WIDTH = 40;
const SEND_DIAMETER = INPUT_MIN_HEIGHT + PILL_PADDING_Y * 2;
const ACTION_HIT_SLOP = { bottom: 0, left: (INPUT_MIN_HEIGHT - ACTION_WIDTH) / 2, right: (INPUT_MIN_HEIGHT - ACTION_WIDTH) / 2, top: 0 };

type ChatComposerProps = {
  value: string;
  onChange: (value: string) => void;
  onSend: () => void;
  onAttach: () => void;
  onCamera: () => void;
  onUnavailable: (label: string) => void;
};

function ComposerAction({ label, onPress, children }: { children: React.ReactNode; label: string; onPress: () => void }) {
  return (
    <NativeRipplePressable accessibilityLabel={label} accessibilityRole="button" hitSlop={ACTION_HIT_SLOP} onPress={onPress} style={styles.action}>
      {children}
    </NativeRipplePressable>
  );
}

export function ChatComposer({ value, onChange, onSend, onAttach, onCamera, onUnavailable }: ChatComposerProps) {
  const theme = useTheme();
  const [emojiOpen, setEmojiOpen] = useState(false);
  const [forcedSelection, setForcedSelection] = useState<{ end: number; start: number } | null>(null);
  const selection = useRef({ end: value.length, start: value.length });
  const text = useRef(value);
  const input = useRef<TextInputInstance | null>(null);
  const caretKept = useRef(false);
  const focused = useRef(false);
  const hasText = value.trim().length > 0;

  useEffect(() => {
    text.current = value;
  }, [value]);

  useEffect(() => {
    if (!emojiOpen) return;
    caretKept.current = true;
    const timer = setTimeout(() => input.current?.focus(), 0);
    return () => {
      clearTimeout(timer);
      caretKept.current = false;
    };
  }, [emojiOpen]);

  const handleSelection = useCallback((event: { nativeEvent: { selection: { end: number; start: number } } }) => {
    const next = trackedSelection(emojiOpen, selection.current, event.nativeEvent.selection, focused.current);
    if (next === selection.current) return;
    selection.current = next;
    setForcedSelection(null);
  }, [emojiOpen]);

  const handleFocus = useCallback(() => {
    focused.current = true;
    if (caretKept.current) return;
    setEmojiOpen(false);
  }, []);

  const handleBlur = useCallback(() => {
    focused.current = false;
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

  const deleteBackward = useCallback(() => {
    const next = deleteBeforeSelection(text.current, selection.current);
    if (next.value === text.current) return;
    text.current = next.value;
    selection.current = next.selection;
    setForcedSelection(next.selection);
    onChange(next.value);
  }, [onChange]);

  const toggleEmoji = useCallback(() => {
    if (emojiOpen) {
      setEmojiOpen(false);
      return;
    }
    Keyboard.dismiss();
    setForcedSelection(selection.current);
    setEmojiOpen(true);
  }, [emojiOpen]);

  const returnToTyping = useCallback(() => {
    if (!emojiOpen) return;
    setForcedSelection(selection.current);
    setEmojiOpen(false);
    setTimeout(() => input.current?.focus(), 0);
  }, [emojiOpen]);

  const restoreCaret = useCallback(() => {
    setForcedSelection(selection.current);
    input.current?.focus();
  }, []);

  return <>
    <XStack alignItems="flex-end" gap={6} marginHorizontal={8} marginVertical={8}>
      <XStack alignItems="flex-end" backgroundColor={theme.backgroundSelected} borderRadius={24} flex={1} gap={2} paddingHorizontal={PILL_PADDING_X} paddingVertical={PILL_PADDING_Y}>
        <ComposerAction label="Abrir emojis" onPress={toggleEmoji}><Smile color={emojiOpen ? theme.accent : theme.textSecondary} size={ICON_SIZE} /></ComposerAction>
        <View onTouchStart={returnToTyping} style={styles.inputWrap}>
          <Input
            accessibilityLabel="Mensaje"
            backgroundColor="transparent"
            borderWidth={0}
            color={theme.text}
            flex={1}
            maxHeight={128}
            maxLength={4000}
            minHeight={INPUT_MIN_HEIGHT}
            multiline
            onChangeText={handleChangeText}
            onBlur={handleBlur}
            onFocus={handleFocus}
            onSelectionChange={handleSelection}
            placeholder="Escribe un mensaje..."
            placeholderTextColor={theme.placeholder}
            ref={input}
            selection={forcedSelection ?? undefined}
            selectionColor={theme.accent}
            showSoftInputOnFocus={!emojiOpen}
            style={styles.input}
            value={value}
          />
        </View>
        <XStack alignItems="center" gap={0}>
          <ComposerAction label="Adjuntar archivo" onPress={onAttach}><Paperclip color={theme.textSecondary} size={ICON_SIZE} /></ComposerAction>
          <ComposerAction label="Abrir cámara" onPress={onCamera}><Camera color={theme.textSecondary} size={ICON_SIZE} /></ComposerAction>
        </XStack>
      </XStack>
      <NativeRipplePressable
        accessibilityLabel={hasText ? 'Enviar mensaje' : 'Grabar nota de voz'}
        accessibilityRole="button"
        onPress={hasText ? onSend : () => onUnavailable('Grabar nota de voz')}
        style={[styles.sendAction, { backgroundColor: theme.backgroundSelected }]}
      >
        {hasText ? <Send color={theme.accent} size={24} /> : <Mic color={theme.accent} size={24} />}
      </NativeRipplePressable>
    </XStack>
    {emojiOpen ? <EmojiPanel onDelete={deleteBackward} onPick={insertEmoji} onSearchClosed={restoreCaret} /> : null}
  </>;
}

const styles = StyleSheet.create({
  action: { alignItems: 'center', borderRadius: ACTION_WIDTH / 2, height: INPUT_MIN_HEIGHT, justifyContent: 'center', width: ACTION_WIDTH },
  input: { paddingHorizontal: 8, paddingVertical: 8, textAlignVertical: 'center' },
  inputWrap: { flex: 1 },
  sendAction: { alignItems: 'center', borderRadius: SEND_DIAMETER / 2, height: SEND_DIAMETER, justifyContent: 'center', overflow: 'hidden', width: SEND_DIAMETER },
});
