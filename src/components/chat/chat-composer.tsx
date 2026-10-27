import { useCallback, useEffect, useRef, useState } from 'react';
import { Keyboard, StyleSheet, View, type TextInputInstance } from 'react-native';
import { Camera, Mic, Paperclip, Send, Smile, Trash2 } from '@tamagui/lucide-icons-2';
import { Input, Text, XStack } from 'tamagui';

import { EmojiPanel } from '@/components/chat/emoji-panel';
import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { deleteBeforeSelection, insertAtSelection, trackedSelection } from '@/domain/composer-text';
import { useVoiceRecorder, type VoiceActivity } from '@/hooks/use-voice-recorder';
import { formatVoiceDuration, voiceNoteTooShortMessage, voiceNoteUsable } from '@/domain/voice-note';
import type { PickedAttachment } from '@/services/attachment-picker';
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
  onVoiceError: (message: string) => void;
  onVoiceNote: (attachment: PickedAttachment) => void;
  onActivity?: (activity: VoiceActivity) => void;
};

function ComposerAction({ label, onPress, children }: { children: React.ReactNode; label: string; onPress: () => void }) {
  return (
    <NativeRipplePressable accessibilityLabel={label} accessibilityRole="button" hitSlop={ACTION_HIT_SLOP} onPress={onPress} style={styles.action}>
      {children}
    </NativeRipplePressable>
  );
}

export function ChatComposer({ value, onChange, onSend, onAttach, onCamera, onVoiceError, onVoiceNote, onActivity }: ChatComposerProps) {
  const theme = useTheme();
  const voice = useVoiceRecorder(onVoiceError, onActivity);
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

  const stopAndSendVoice = useCallback(async () => {
    const seconds = voice.seconds;
    const attachment = await voice.stop();
    if (!attachment) return;
    if (!voiceNoteUsable(seconds)) {
      onVoiceError(voiceNoteTooShortMessage());
      return;
    }
    onVoiceNote(attachment);
  }, [onVoiceError, onVoiceNote, voice]);

  const handleMic = useCallback(() => {
    if (voice.recording) {
      void stopAndSendVoice();
      return;
    }
    setEmojiOpen(false);
    void voice.start();
  }, [stopAndSendVoice, voice]);

  const restoreCaret = useCallback(() => {
    setForcedSelection(selection.current);
    input.current?.focus();
  }, []);

  return <>
    <XStack alignItems="flex-end" gap={6} marginHorizontal={8} marginVertical={8}>
      <XStack alignItems="flex-end" backgroundColor={theme.backgroundSelected} borderRadius={24} flex={1} gap={2} paddingHorizontal={PILL_PADDING_X} paddingVertical={PILL_PADDING_Y}>
        {voice.recording ? <>
            <View style={styles.recording}>
              <View style={[styles.recordingDot, { backgroundColor: theme.danger }]} />
              <Text color={theme.text} fontSize={15} fontWeight="700" style={styles.recordingTime}>{formatVoiceDuration(voice.seconds)}</Text>
              <Text color={theme.placeholder} fontSize={12} numberOfLines={1}>Grabando…</Text>
            </View>
            <ComposerAction label="Descartar nota de voz" onPress={voice.cancel}><Trash2 color={theme.danger} size={ICON_SIZE} /></ComposerAction>
        </> : <>
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
        </>}
      </XStack>
      <NativeRipplePressable
        accessibilityLabel={voice.recording ? 'Enviar nota de voz' : hasText ? 'Enviar mensaje' : 'Grabar nota de voz'}
        accessibilityRole="button"
        onPress={voice.recording ? () => { void stopAndSendVoice(); } : hasText ? onSend : handleMic}
        style={[styles.sendAction, { backgroundColor: theme.backgroundSelected }]}
      >
        {voice.recording || hasText ? <Send color={theme.accent} size={24} /> : <Mic color={theme.accent} size={24} />}
      </NativeRipplePressable>
    </XStack>
    {emojiOpen ? <EmojiPanel onDelete={deleteBackward} onPick={insertEmoji} onSearchClosed={restoreCaret} /> : null}
  </>;
}

const styles = StyleSheet.create({
  action: { alignItems: 'center', borderRadius: ACTION_WIDTH / 2, height: INPUT_MIN_HEIGHT, justifyContent: 'center', width: ACTION_WIDTH },
  input: { paddingHorizontal: 8, paddingVertical: 8, textAlignVertical: 'center' },
  recording: { alignItems: 'center', flex: 1, flexDirection: 'row', gap: 8, minHeight: INPUT_MIN_HEIGHT, paddingHorizontal: 10 },
  recordingDot: { borderRadius: 5, height: 10, width: 10 },
  recordingTime: { fontVariant: 'tabular-nums' },
  inputWrap: { flex: 1 },
  sendAction: { alignItems: 'center', borderRadius: SEND_DIAMETER / 2, height: SEND_DIAMETER, justifyContent: 'center', overflow: 'hidden', width: SEND_DIAMETER },
});
