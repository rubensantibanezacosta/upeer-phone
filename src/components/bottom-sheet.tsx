import type { ReactNode } from 'react';
import { Modal, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { X } from '@tamagui/lucide-icons-2';
import { SizableText, XStack } from 'tamagui';

import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { useTheme } from '@/hooks/use-theme';

type BottomSheetProps = {
  visible: boolean;
  title: string;
  icon?: ReactNode;
  onClose: () => void;
  children: ReactNode;
};

export function BottomSheet({ visible, title, icon, onClose, children }: BottomSheetProps) {
  const theme = useTheme();
  const insets = useSafeAreaInsets();

  return <Modal animationType="fade" onRequestClose={onClose} presentationStyle="overFullScreen" statusBarTranslucent transparent visible={visible}>
    <View style={styles.overlay}>
      <Pressable accessibilityLabel="Cerrar" onPress={onClose} style={[StyleSheet.absoluteFill, { backgroundColor: theme.backdrop }]} />
      <View accessibilityViewIsModal style={[styles.sheet, { backgroundColor: theme.backgroundElement, borderColor: theme.border, paddingBottom: Math.max(insets.bottom, 12) }]}>
        <XStack alignItems="center" gap={12} paddingHorizontal={16} paddingVertical={14}>
          {icon}
          <SizableText color={theme.text} flex={1} fontSize={16} fontWeight="700" numberOfLines={1}>{title}</SizableText>
          <NativeRipplePressable accessibilityLabel="Cerrar" onPress={onClose} style={styles.closeButton}><X color={theme.placeholder} size={18} /></NativeRipplePressable>
        </XStack>
        <View style={[styles.divider, { backgroundColor: theme.border }]} />
        <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled" style={styles.scroll}>{children}</ScrollView>
      </View>
    </View>
  </Modal>;
}

const styles = StyleSheet.create({
  overlay: { flex: 1, justifyContent: 'flex-end', paddingHorizontal: 12 },
  sheet: { borderRadius: 18, borderWidth: StyleSheet.hairlineWidth, elevation: 14, maxHeight: '88%', overflow: 'hidden', shadowColor: '#000', shadowOffset: { height: 8, width: 0 }, shadowOpacity: 0.32, shadowRadius: 18 },
  scroll: { flexGrow: 0 },
  content: { gap: 12, paddingHorizontal: 16, paddingVertical: 14 },
  closeButton: { alignItems: 'center', borderRadius: 20, height: 40, justifyContent: 'center', width: 40 },
  divider: { height: StyleSheet.hairlineWidth, marginHorizontal: 16 },
});
