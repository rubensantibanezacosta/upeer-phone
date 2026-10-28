import { Modal, Pressable, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Check, Palette } from '@tamagui/lucide-icons-2';
import { ScrollView, SizableText, Text, XStack } from 'tamagui';

import { CHAT_STYLE_PRESETS, resolveChatStyleId } from '@/domain/chat-style';
import { ChatStyleBackground } from '@/components/chat/chat-style-background';
import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

type ChatStylePickerProps = {
  onClose: () => void;
  onSelect: (id: string) => void;
  value?: string | null;
  visible: boolean;
};

export function ChatStylePicker({ onClose, onSelect, value, visible }: ChatStylePickerProps) {
  const theme = useTheme();
  const scale = useTextScale();
  const insets = useSafeAreaInsets();
  const current = resolveChatStyleId(value);

  return (
    <Modal animationType="fade" onRequestClose={onClose} presentationStyle="overFullScreen" statusBarTranslucent transparent visible={visible}>
      <View style={styles.overlay}>
        <Pressable accessibilityLabel="Cerrar estilos de chat" onPress={onClose} style={[StyleSheet.absoluteFill, { backgroundColor: theme.backdrop }]} />
        <View accessibilityViewIsModal style={[styles.sheet, { backgroundColor: theme.backgroundElement, borderColor: theme.border, paddingBottom: Math.max(insets.bottom, 12) }]}>
          <XStack alignItems="center" gap={10} paddingHorizontal={16} paddingVertical={14}>
            <Palette color={theme.text} size={18} />
            <SizableText color={theme.text} flex={1} fontSize={scale(16)} fontWeight="700">Estilo del chat</SizableText>
          </XStack>
          <View style={[styles.divider, { backgroundColor: theme.border }]} />
          <Text color={theme.placeholder} fontSize={scale(12)} paddingHorizontal={16} paddingTop={12}>Solo cambia el fondo de esta conversación.</Text>
          <ScrollView contentContainerStyle={styles.grid} showsVerticalScrollIndicator={false}>
            {CHAT_STYLE_PRESETS.map((preset) => {
              const selected = preset.id === current;
              return (
                <NativeRipplePressable
                  accessibilityRole="radio"
                  accessibilityState={{ checked: selected }}
                  key={preset.id}
                  onPress={() => onSelect(preset.id)}
                  style={[styles.option, { backgroundColor: theme.background, borderColor: selected ? theme.accent : theme.border }]}
                >
                  <View style={styles.swatch}>
                    <ChatStyleBackground styleId={preset.id} />
                    {selected ? <View style={styles.check}><Check color={theme.accent} size={16} /></View> : null}
                  </View>
                  <Text color={theme.text} fontSize={scale(12)} fontWeight="600" numberOfLines={1}>{preset.label}</Text>
                  <Text color={theme.placeholder} fontSize={scale(10)} numberOfLines={1}>{preset.description}</Text>
                </NativeRipplePressable>
              );
            })}
          </ScrollView>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: { flex: 1, justifyContent: 'flex-end', paddingHorizontal: 12 },
  sheet: { borderRadius: 18, borderWidth: StyleSheet.hairlineWidth, elevation: 14, maxHeight: '75%', overflow: 'hidden', shadowColor: '#000', shadowOffset: { height: 8, width: 0 }, shadowOpacity: 0.32, shadowRadius: 18 },
  divider: { height: StyleSheet.hairlineWidth, marginHorizontal: 16 },
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: 10, padding: 16 },
  option: { borderRadius: 12, borderWidth: 1.5, gap: 4, overflow: 'hidden', padding: 8, width: '47%' },
  swatch: { borderRadius: 8, height: 56, overflow: 'hidden', position: 'relative', width: '100%' },
  check: { alignItems: 'center', bottom: 4, position: 'absolute', right: 4 },
});
