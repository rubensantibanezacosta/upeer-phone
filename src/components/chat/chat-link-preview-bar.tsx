import { StyleSheet, View } from 'react-native';
import { X } from '@tamagui/lucide-icons-2';
import { Text, XStack } from 'tamagui';

import { ChatLinkPreview } from '@/components/chat/chat-link-preview';
import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import type { LinkPreview } from '@/domain/link-preview';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

type ChatLinkPreviewBarProps = {
  loading: boolean;
  onDismiss: () => void;
  preview: LinkPreview | null;
};

export function ChatLinkPreviewBar({ loading, onDismiss, preview }: ChatLinkPreviewBarProps) {
  const theme = useTheme();
  const scale = useTextScale();
  if (!preview) {
    return loading
      ? <View style={styles.bar}><Text color={theme.placeholder} fontSize={scale(11)}>Cargando vista previa…</Text></View>
      : null;
  }
  return <XStack alignItems="flex-start" gap={6} paddingHorizontal={12} paddingTop={6}>
    <View style={styles.card}>
      <ChatLinkPreview preview={preview} tone="theirs" />
    </View>
    <NativeRipplePressable accessibilityLabel="Descartar la vista previa" accessibilityRole="button" onPress={onDismiss} style={styles.close}>
      <X color={theme.placeholder} size={15} />
    </NativeRipplePressable>
  </XStack>;
}

const styles = StyleSheet.create({
  bar: { paddingHorizontal: 16, paddingTop: 6 },
  card: { flex: 1 },
  close: { alignItems: 'center', borderRadius: 14, height: 28, justifyContent: 'center', width: 28 },
});
