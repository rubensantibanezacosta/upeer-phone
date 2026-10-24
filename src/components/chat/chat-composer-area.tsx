import { StyleSheet, View } from 'react-native';
import { Pencil, Reply, X } from '@tamagui/lucide-icons-2';
import { Text, XStack } from 'tamagui';

import { ChatComposer } from '@/components/chat/chat-composer';
import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import type { ReplyPreview } from '@/domain/chat-reply';
import { useTheme } from '@/hooks/use-theme';

type ChatComposerAreaProps = {
  editing: boolean;
  error: string;
  hidden: boolean;
  onChange: (value: string) => void;
  onCancelContext: () => void;
  onSend: () => void;
  onUnavailable: (label: string) => void;
  reply: ReplyPreview | null;
  value: string;
};

export function ChatComposerArea({ editing, error, hidden, onChange, onCancelContext, onSend, onUnavailable, reply, value }: ChatComposerAreaProps) {
  const theme = useTheme();
  const context = editing ? { icon: <Pencil color={theme.accent} size={15} />, label: 'Editando mensaje', detail: '' }
    : reply ? { icon: <Reply color={theme.accent} size={15} />, label: `Respondiendo a ${reply.author}`, detail: reply.excerpt }
      : null;

  return <>
    {error ? (
      <XStack alignItems="center" backgroundColor={theme.backgroundElement} borderTopColor={theme.border} borderTopWidth={StyleSheet.hairlineWidth} gap={7} paddingHorizontal={14} paddingVertical={7}>
        <Text color={theme.danger} flex={1} fontSize={12}>{error}</Text>
      </XStack>
    ) : null}
    {context ? (
      <XStack alignItems="center" backgroundColor={theme.backgroundSelected} borderTopColor={theme.border} borderTopWidth={StyleSheet.hairlineWidth} gap={8} paddingHorizontal={14} paddingVertical={7}>
        {context.icon}
        <View style={styles.contextText}>
          <Text color={theme.text} fontSize={12} fontWeight="600" numberOfLines={1}>{context.label}</Text>
          {context.detail ? <Text color={theme.textSecondary} fontSize={11} numberOfLines={1}>{context.detail}</Text> : null}
        </View>
        <NativeRipplePressable accessibilityLabel="Cancelar" accessibilityRole="button" onPress={onCancelContext} style={styles.cancel}>
          <X color={theme.textSecondary} size={18} />
        </NativeRipplePressable>
      </XStack>
    ) : null}
    {hidden ? null : <ChatComposer onChange={onChange} onSend={onSend} onUnavailable={onUnavailable} value={value} />}
  </>;
}

const styles = StyleSheet.create({
  contextText: { flex: 1, gap: 1 },
  cancel: { alignItems: 'center', borderRadius: 6, height: 28, justifyContent: 'center', width: 28 },
});
