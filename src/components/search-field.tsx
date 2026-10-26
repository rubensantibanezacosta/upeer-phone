import { Search, X } from '@tamagui/lucide-icons-2';
import { Input, XStack } from 'tamagui';

import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

const HEIGHT = 54;
const TEXT_HEIGHT = 24;
const FIELD_RADIUS = HEIGHT / 2;

type SearchFieldProps = {
  onChangeText: (value: string) => void;
  placeholder: string;
  value: string;
};

export function SearchField({ onChangeText, placeholder, value }: SearchFieldProps) {
  const theme = useTheme();
  const scale = useTextScale();

  return (
    <XStack alignItems="center" backgroundColor={theme.backgroundElement} borderRadius={FIELD_RADIUS} gap={8} height={HEIGHT} paddingHorizontal={14}>
      <Search color={theme.placeholder} size={18} />
      <Input
        backgroundColor="transparent"
        borderWidth={0}
        color={theme.text}
        flex={1}
        fontSize={scale(14)}
        height={TEXT_HEIGHT}
        onChangeText={onChangeText}
        padding={0}
        placeholder={placeholder}
        placeholderTextColor={theme.placeholder}
        value={value}
      />
      {value.length > 0 ? (
        <NativeRipplePressable
          accessibilityLabel="Limpiar búsqueda"
          accessibilityRole="button"
          onPress={() => onChangeText('')}
          style={styles.clear}
        >
          <X color={theme.placeholder} size={16} />
        </NativeRipplePressable>
      ) : null}
    </XStack>
  );
}

const styles = { clear: { alignItems: 'center' as const, height: 24, justifyContent: 'center' as const, width: 24 } };
