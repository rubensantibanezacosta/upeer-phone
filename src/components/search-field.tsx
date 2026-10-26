import { Search, X } from '@tamagui/lucide-icons-2';
import { Input, XStack } from 'tamagui';

import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

const HEIGHT = 54;
const TEXT_HEIGHT = 24;
const ICON_SIZE = 18;
const ICON_INSET = (HEIGHT - ICON_SIZE) / 2;
const CLEAR_SIZE = 24;
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
    <XStack alignItems="center" backgroundColor={theme.backgroundElement} borderRadius={FIELD_RADIUS} gap={8} height={HEIGHT} paddingHorizontal={ICON_INSET}>
      <Search color={theme.placeholder} size={ICON_SIZE} />
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

const styles = { clear: { alignItems: 'center' as const, height: CLEAR_SIZE, justifyContent: 'center' as const, marginRight: -(CLEAR_SIZE - ICON_SIZE) / 2, width: CLEAR_SIZE } };
