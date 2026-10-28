import { Modal, Pressable, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Check, Timer } from '@tamagui/lucide-icons-2';
import { SizableText, Text, XStack, YStack } from 'tamagui';

import { RETENTION_PRESETS, resolveRetentionId } from '@/domain/retention';
import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

type ChatRetentionPickerProps = {
  onClose: () => void;
  onSelect: (id: string) => void;
  value?: string | null;
  visible: boolean;
};

export function ChatRetentionPicker({ onClose, onSelect, value, visible }: ChatRetentionPickerProps) {
  const theme = useTheme();
  const scale = useTextScale();
  const insets = useSafeAreaInsets();
  const current = resolveRetentionId(value);

  return (
    <Modal animationType="fade" onRequestClose={onClose} presentationStyle="overFullScreen" statusBarTranslucent transparent visible={visible}>
      <View style={styles.overlay}>
        <Pressable accessibilityLabel="Cerrar mensajes temporales" onPress={onClose} style={[StyleSheet.absoluteFill, { backgroundColor: theme.backdrop }]} />
        <View accessibilityViewIsModal style={[styles.sheet, { backgroundColor: theme.backgroundElement, borderColor: theme.border, paddingBottom: Math.max(insets.bottom, 12) }]}>
          <XStack alignItems="center" gap={10} paddingHorizontal={16} paddingVertical={14}>
            <Timer color={theme.text} size={18} />
            <SizableText color={theme.text} flex={1} fontSize={scale(16)} fontWeight="700">Mensajes temporales</SizableText>
          </XStack>
          <View style={[styles.divider, { backgroundColor: theme.border }]} />
          <YStack paddingHorizontal={16} paddingVertical={12}>
            <Text color={theme.placeholder} fontSize={scale(12)}>
              Los mensajes nuevos de esta conversación se borran de este dispositivo al cumplirse el plazo. Los que ya existen no cambian.
            </Text>
          </YStack>
          {RETENTION_PRESETS.map((preset) => {
            const selected = preset.id === current;
            return (
              <NativeRipplePressable accessibilityRole="radio" accessibilityState={{ checked: selected }} key={preset.id} onPress={() => onSelect(preset.id)} style={styles.option}>
                <Text color={theme.text} flex={1} fontSize={scale(14)} fontWeight="600">{preset.label}</Text>
                {selected ? <Check color={theme.accent} size={18} /> : null}
              </NativeRipplePressable>
            );
          })}
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: { flex: 1, justifyContent: 'flex-end', paddingHorizontal: 12 },
  sheet: { borderRadius: 18, borderWidth: StyleSheet.hairlineWidth, elevation: 14, overflow: 'hidden', shadowColor: '#000', shadowOffset: { height: 8, width: 0 }, shadowOpacity: 0.32, shadowRadius: 18 },
  option: { alignItems: 'center', flexDirection: 'row', gap: 12, minHeight: 48, paddingHorizontal: 18 },
  divider: { height: StyleSheet.hairlineWidth, marginHorizontal: 16 },
});
