import { Card, Paragraph, SizableText, YStack } from 'tamagui';

import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import type { AppearanceTheme } from '@/domain/preferences';
import { usePreferences } from '@/hooks/use-preferences';
import { useTheme } from '@/hooks/use-theme';
import { updatePreferences } from '@/storage/preferences-store';

const THEME_OPTIONS: { value: AppearanceTheme; label: string; description: string }[] = [
  { value: 'system', label: 'Seguir sistema', description: 'Cambia con el tema del teléfono' },
  { value: 'light', label: 'Claro', description: 'Fondo blanco en toda la app' },
  { value: 'dark', label: 'Oscuro', description: 'Fondo negro en toda la app' },
];

export function AppearanceSettingsSection() {
  const theme = useTheme();
  const preferences = usePreferences();

  return <YStack gap={12}>
    <Card backgroundColor={theme.backgroundElement} borderColor={theme.border} borderRadius={14} borderWidth={1} gap={6} padding={16}>
      <SizableText color={theme.text} fontSize={22} fontWeight="700">Apariencia</SizableText>
      <Paragraph color={theme.placeholder} fontSize={13}>Elige el tema de la app. El cambio se aplica al instante y se recuerda en este dispositivo.</Paragraph>
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

    {preferences.fontSize === 'medium' ? null : <Paragraph color={theme.placeholder} fontSize={12}>El tamaño de texto guardado se aplicará cuando todas las pantallas usen la escala común.</Paragraph>}
  </YStack>;
}
