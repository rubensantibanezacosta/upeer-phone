import { useEffect, useRef } from 'react';
import { StyleSheet } from 'react-native';
import { Delete, Search } from '@tamagui/lucide-icons-2';
import { SizableText, XStack } from 'tamagui';

import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

const REPEAT_MS = 70;

type EmojiToolbarProps = {
  label: string;
  onDelete?: () => void;
  onSearch: () => void;
  searchOpen: boolean;
};

export function EmojiToolbar({ label, onDelete, onSearch, searchOpen }: EmojiToolbarProps) {
  const theme = useTheme();
  const scale = useTextScale();
  const repeat = useRef<ReturnType<typeof setInterval> | null>(null);
  const repeated = useRef(false);

  const stop = () => {
    if (repeat.current) clearInterval(repeat.current);
    repeat.current = null;
  };

  useEffect(() => stop, []);

  return <XStack alignItems="center" gap={6} paddingHorizontal={2}>
    <NativeRipplePressable
      accessibilityLabel={searchOpen ? 'Cerrar la búsqueda de emojis' : 'Buscar emojis'}
      accessibilityRole="button"
      accessibilityState={{ expanded: searchOpen }}
      onPress={onSearch}
      style={[styles.action, { backgroundColor: searchOpen ? theme.backgroundSelected : 'transparent' }]}
    >
      <Search color={searchOpen ? theme.accent : theme.placeholder} size={19} />
    </NativeRipplePressable>
    <SizableText color={theme.placeholder} flex={1} fontSize={scale(11)} fontWeight="600" numberOfLines={1} textAlign="center">{label}</SizableText>
    {onDelete ? <NativeRipplePressable
      accessibilityLabel="Borrar"
      accessibilityRole="button"
      onLongPress={() => {
        repeated.current = true;
        onDelete();
        repeat.current = setInterval(onDelete, REPEAT_MS);
      }}
      onPress={() => {
        if (repeated.current) {
          repeated.current = false;
          return;
        }
        onDelete();
      }}
      onPressIn={() => {
        repeated.current = false;
        stop();
      }}
      onPressOut={stop}
      style={styles.action}
    >
      <Delete color={theme.textSecondary} size={20} />
    </NativeRipplePressable> : null}
  </XStack>;
}

const styles = StyleSheet.create({
  action: { alignItems: 'center', borderRadius: 8, height: 32, justifyContent: 'center', width: 40 },
});
