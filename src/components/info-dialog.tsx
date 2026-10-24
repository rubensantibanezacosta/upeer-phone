import { Modal, ScrollView, StyleSheet } from 'react-native';
import { Info, X } from '@tamagui/lucide-icons-2';
import { SizableText, Text, XStack, YStack } from 'tamagui';

import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { useTheme } from '@/hooks/use-theme';

export type InfoDialogContent = {
  title: string;
  description: string;
  items: { label: string; description: string }[];
};

export function InfoDialog({ content, onClose }: { content: InfoDialogContent | null; onClose: () => void }) {
  const theme = useTheme();
  if (!content) return null;

  return <Modal animationType="fade" onRequestClose={onClose} transparent visible>
    <NativeRipplePressable android_ripple={{ color: 'transparent' }} onPress={onClose} style={[styles.backdrop, { backgroundColor: theme.backdrop }]}>
      <NativeRipplePressable onPress={(event) => event.stopPropagation()} style={[styles.dialog, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
        <XStack alignItems="center" borderBottomColor={theme.border} borderBottomWidth={1} gap={12} paddingBottom={14}>
          <Info color={theme.placeholder} size={18} />
          <SizableText color={theme.text} flex={1} fontSize={17} fontWeight="700">{content.title}</SizableText>
          <NativeRipplePressable accessibilityLabel="Cerrar" onPress={onClose}><X color={theme.placeholder} size={20} /></NativeRipplePressable>
        </XStack>
        <ScrollView contentContainerStyle={styles.content}>
          <Text color={theme.placeholder} fontSize={14}>{content.description}</Text>
          {content.items.map((item) => <YStack gap={4} key={item.label}>
            <SizableText color={theme.text} fontSize={14} fontWeight="600">{item.label}</SizableText>
            <Text color={theme.placeholder} fontSize={13}>{item.description}</Text>
          </YStack>)}
        </ScrollView>
      </NativeRipplePressable>
    </NativeRipplePressable>
  </Modal>;
}

const styles = StyleSheet.create({
  backdrop: { alignItems: 'center', flex: 1, justifyContent: 'center', padding: 20 },
  dialog: { borderRadius: 16, borderWidth: 1, maxHeight: '82%', maxWidth: 480, padding: 20, width: '100%' },
  content: { gap: 14, paddingVertical: 18 },
});
