import type { ReactNode } from 'react';
import { Modal, Pressable, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { X } from '@tamagui/lucide-icons-2';
import { SizableText, XStack } from 'tamagui';

import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

type EmojiSheetProps = {
  visible: boolean;
  title: string;
  icon?: ReactNode;
  onClose: () => void;
  children: ReactNode;
};

export function EmojiSheet({ visible, title, icon, onClose, children }: EmojiSheetProps) {
  const theme = useTheme();
  const scale = useTextScale();
  const insets = useSafeAreaInsets();

  return <Modal animationType="fade" onRequestClose={onClose} presentationStyle="overFullScreen" statusBarTranslucent transparent visible={visible}>
    <View style={styles.overlay}>
      <Pressable accessibilityLabel="Cerrar" onPress={onClose} style={[StyleSheet.absoluteFill, { backgroundColor: theme.backdrop }]} />
      <View accessibilityViewIsModal style={[styles.sheet, { backgroundColor: theme.backgroundElement, borderColor: theme.border, paddingBottom: Math.max(insets.bottom, 10) }]}>
        <XStack alignItems="center" gap={8} paddingHorizontal={12} paddingVertical={10}>
          {icon}
          <SizableText color={theme.text} flex={1} fontSize={scale(15)} fontWeight="700">{title}</SizableText>
          <NativeRipplePressable accessibilityLabel="Cerrar" onPress={onClose} style={styles.closeButton}><X color={theme.placeholder} size={17} /></NativeRipplePressable>
        </XStack>
        <View style={[styles.divider, { backgroundColor: theme.border }]} />
        <View style={styles.content}>{children}</View>
      </View>
    </View>
  </Modal>;
}

const styles = StyleSheet.create({
  closeButton: { alignItems: 'center', borderRadius: 18, height: 36, justifyContent: 'center', width: 36 },
  content: { gap: 8, paddingHorizontal: 12, paddingVertical: 10 },
  divider: { height: StyleSheet.hairlineWidth },
  overlay: { flex: 1, justifyContent: 'flex-end', paddingHorizontal: 10, paddingBottom: 10 },
  sheet: { borderRadius: 16, borderWidth: StyleSheet.hairlineWidth, elevation: 14, overflow: 'hidden', shadowColor: '#000', shadowOffset: { height: 8, width: 0 }, shadowOpacity: 0.32, shadowRadius: 18 },
});
