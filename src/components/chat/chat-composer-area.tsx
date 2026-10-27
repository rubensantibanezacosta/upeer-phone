import { StyleSheet, View } from 'react-native';
import { CircleAlert, Pencil, Reply, X } from '@tamagui/lucide-icons-2';
import { Text, XStack } from 'tamagui';

import { ChatComposer } from '@/components/chat/chat-composer';
import { ChatLinkPreviewBar } from '@/components/chat/chat-link-preview-bar';
import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import type { ReplyPreview } from '@/domain/chat-reply';
import type { LinkPreview } from '@/domain/link-preview';
import type { PickedAttachment } from '@/services/attachment-picker';
import type { VoiceActivity } from '@/hooks/use-voice-recorder';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

type ChatComposerAreaProps = {
  editing: boolean;
  error: string;
  hidden: boolean;
  loadingPreview: boolean;
  onChange: (value: string) => void;
  onCancelContext: () => void;
  onDismissPreview: () => void;
  onSend: () => void;
  onAttach: () => void;
  onCamera: () => void;
  onVoiceError: (message: string) => void;
  onVoiceNote: (attachment: PickedAttachment) => void;
  onActivity?: (activity: VoiceActivity) => void;
  preview: LinkPreview | null;
  reply: ReplyPreview | null;
  value: string;
};

export function ChatComposerArea({ editing, error, hidden, loadingPreview, onChange, onAttach, onCamera, onCancelContext, onDismissPreview, onSend, onVoiceError, onVoiceNote, onActivity, preview, reply, value }: ChatComposerAreaProps) {
  const theme = useTheme();
  const scale = useTextScale();
  const context = editing ? { icon: <Pencil color={theme.accent} size={15} />, label: 'Editando mensaje', detail: '' }
    : reply ? { icon: <Reply color={theme.accent} size={15} />, label: 'Respondiendo a', detail: reply.excerpt, author: reply.author }
      : null;

  return <>
    {error ? (
      <XStack alignItems="center" backgroundColor={theme.backgroundElement} gap={7} paddingHorizontal={14} paddingVertical={7}>
        <CircleAlert color={theme.danger} size={15} />
        <Text color={theme.danger} flex={1} fontSize={scale(12)}>{error}</Text>
      </XStack>
    ) : null}
    {context ? (
      <XStack alignItems="center" backgroundColor={theme.backgroundSelected} borderLeftColor={theme.accent} borderLeftWidth={3} borderTopColor={theme.border} borderTopWidth={StyleSheet.hairlineWidth} gap={8} paddingHorizontal={14} paddingVertical={7}>
        {context.icon}
        <View style={styles.contextText}>
          <Text color={theme.accent} fontSize={scale(11)} fontWeight="700" numberOfLines={1}>
            {context.author ? `${context.label} ${context.author}` : context.label}
          </Text>
          {context.detail ? <Text color={theme.textSecondary} fontSize={scale(12)} numberOfLines={1} opacity={0.85}>{context.detail}</Text> : null}
        </View>
        <NativeRipplePressable accessibilityLabel="Cancelar" accessibilityRole="button" onPress={onCancelContext} style={styles.cancel}>
          <X color={theme.textSecondary} size={18} />
        </NativeRipplePressable>
      </XStack>
    ) : null}
    {hidden ? null : <ChatLinkPreviewBar loading={loadingPreview} onDismiss={onDismissPreview} preview={preview} />}
    {hidden ? null : <ChatComposer onActivity={onActivity} onAttach={onAttach} onCamera={onCamera} onChange={onChange} onSend={onSend} onVoiceError={onVoiceError} onVoiceNote={onVoiceNote} value={value} />}
  </>;
}

const styles = StyleSheet.create({
  contextText: { flex: 1, gap: 0, paddingLeft: 8 },
  cancel: { alignItems: 'center', borderRadius: 6, height: 28, justifyContent: 'center', width: 28 },
});
