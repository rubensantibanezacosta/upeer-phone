import { Card, Paragraph, SizableText, XStack, YStack } from 'tamagui';

import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import type { AppearanceFontSize, AppearanceTheme } from '@/domain/preferences';
import { describeFontScale } from '@/domain/text-scale';
import { usePreferences } from '@/hooks/use-preferences';
import { useTheme } from '@/hooks/use-theme';
import { updatePreferences } from '@/storage/preferences-store';

const THEME_OPTIONS: { value: AppearanceTheme; label: string; description: string }[] = [
  { value: 'system', label: 'Seguir sistema', description: 'Cambia con el tema del teléfono' },
  { value: 'light', label: 'Claro', description: 'Fondo blanco en toda la app' },
  { value: 'dark', label: 'Oscuro', description: 'Fondo negro en toda la app' },
];

const FONT_OPTIONS: { value: AppearanceFontSize; label: string; size: number }[] = [
  { value: 'small', label: 'Pequeño', size: 13 },
  { value: 'medium', label: 'Normal', size: 16 },
  { value: 'large', label: 'Grande', size: 20 },
];

export function AppearanceSettingsSection() {
  const theme = useTheme();
  const preferences = usePreferences();

  return <YStack gap={12}>
    <Card backgroundColor={theme.backgroundElement} borderColor={theme.border} borderRadius={14} borderWidth={1} gap={6} padding={16}>
      <SizableText color={theme.text} fontSize={22} fontWeight="700">Apariencia</SizableText>
      <Paragraph color={theme.placeholder} fontSize={13}>Elige el tema y el tamaño del texto. Los cambios se aplican al instante y se recuerdan en este dispositivo.</Paragraph>
    </Card>

    <YStack borderColor={theme.border} borderRadius={14} borderWidth={1} overflow="hidden">
      {THEME_OPTIONS.map((option) => <NativeRipplePressable
        accessibilityRole="radio"
        accessibilityState={{ selected: preferences.theme === option.value }}
        key={option.value}
        onPress={() => void updatePreferences({ theme: option.value })}
        style={{ alignItems: 'center', borderBottomColor: theme.border, borderBottomWidth: 1, flexDirection: 'row', gap: 12, minHeight: 64, paddingHorizontal: 16, paddingVertical: 12 }}
      >
        <YStack flex={1} gap={2}>
          <SizableText color={theme.text} fontSize={15} fontWeight="500">{option.label}</SizableText>
          <Paragraph color={theme.placeholder} fontSize={12}>{option.description}</Paragraph>
        </YStack>
        <YStack borderColor={preferences.theme === option.value ? theme.accent : theme.border} borderRadius={10} borderWidth={2} height={20} width={20}>
          {preferences.theme === option.value ? <YStack backgroundColor={theme.accent} borderRadius={5} flex={1} margin={3} /> : null}
        </YStack>
      </NativeRipplePressable>)}
    </YStack>

    <YStack borderColor={theme.border} borderRadius={14} borderWidth={1} gap={4} overflow="hidden" padding={16}>
      <SizableText color={theme.text} fontSize={15} fontWeight="700">Tamaño del texto</SizableText>
      <Paragraph color={theme.placeholder} fontSize={12}>Ajusta el tamaño de los mensajes, la lista de Chats y la cabecera. Se recuerda en este dispositivo.</Paragraph>
      <XStack backgroundColor={theme.backgroundSelected} borderRadius={12} gap={4} marginTop={8} padding={4}>
        {FONT_OPTIONS.map((option) => {
          const active = preferences.fontSize === option.value;
          return <NativeRipplePressable
            accessibilityLabel={`Texto ${option.label.toLowerCase()}`}
            accessibilityRole="radio"
            accessibilityState={{ selected: active }}
            key={option.value}
            onPress={() => void updatePreferences({ fontSize: option.value })}
            style={{ alignItems: 'center', backgroundColor: active ? theme.accent : 'transparent', borderRadius: 9, flex: 1, gap: 2, justifyContent: 'center', minHeight: 56 }}
          >
            <SizableText color={active ? theme.onAccent : theme.text} fontSize={option.size} fontWeight="700">Aa</SizableText>
            <SizableText color={active ? theme.onAccent : theme.placeholder} fontSize={11}>{option.label}</SizableText>
          </NativeRipplePressable>;
        })}
      </XStack>
      <Paragraph color={theme.placeholder} fontSize={11} marginTop={4}>{describeFontScale(preferences.fontSize)}</Paragraph>
    </YStack>
  </YStack>;
}