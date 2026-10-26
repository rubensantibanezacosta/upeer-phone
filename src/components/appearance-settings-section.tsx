import { ALargeSmall, Check, Palette, SunMoon } from '@tamagui/lucide-icons-2';
import { Paragraph, SizableText, Text, XStack, YStack } from 'tamagui';

import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { SettingsGroupLabel, SettingsSectionHeader } from '@/components/settings-section-parts';
import type { AppearanceFontSize, AppearanceTheme } from '@/domain/preferences';
import { describeFontScale } from '@/domain/text-scale';
import { usePreferences } from '@/hooks/use-preferences';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';
import { updatePreferences } from '@/storage/preferences-store';

const THEME_OPTIONS: { value: AppearanceTheme; label: string }[] = [
  { value: 'dark', label: 'Oscuro' },
  { value: 'light', label: 'Claro' },
  { value: 'system', label: 'Sistema' },
];

const FONT_OPTIONS: { value: AppearanceFontSize; label: string; size: number }[] = [
  { value: 'small', label: 'Pequeño', size: 15 },
  { value: 'medium', label: 'Normal', size: 19 },
  { value: 'large', label: 'Grande', size: 23 },
];

type ThemePalette = { bar: string; canvas: string; line: string; surface: string };

const PALETTES: Record<'dark' | 'light', ThemePalette> = {
  dark: { bar: '#33373D', canvas: '#161719', line: '#4B4F56', surface: '#22252A' },
  light: { bar: '#DCE0E6', canvas: '#FFFFFF', line: '#B9C0C8', surface: '#EFF1F4' },
};

function PreviewPane({ palette }: { palette: ThemePalette }) {
  return (
    <YStack backgroundColor={palette.canvas} flex={1} gap={4} justifyContent="center" padding={7}>
      <YStack backgroundColor={palette.bar} borderRadius={3} height={5} width="60%" />
      <YStack backgroundColor={palette.surface} borderRadius={4} gap={3} padding={5}>
        <YStack backgroundColor={palette.line} borderRadius={2} height={3} width="85%" />
        <YStack backgroundColor={palette.line} borderRadius={2} height={3} width="55%" />
      </YStack>
      <YStack alignItems="flex-end">
        <YStack backgroundColor={palette.bar} borderRadius={4} height={8} width="45%" />
      </YStack>
    </YStack>
  );
}

function ThemePreview({ option }: { option: AppearanceTheme }) {
  if (option === 'system') {
    return (
      <XStack flex={1}>
        <PreviewPane palette={PALETTES.light} />
        <PreviewPane palette={PALETTES.dark} />
      </XStack>
    );
  }
  return <PreviewPane palette={option === 'dark' ? PALETTES.dark : PALETTES.light} />;
}

export function AppearanceSettingsSection() {
  const theme = useTheme();
  const scale = useTextScale();
  const preferences = usePreferences();

  return <YStack gap={12}>
    <SettingsSectionHeader icon={<Palette color={theme.accent} size={18} />} title="Apariencia">Elige el tema y el tamaño del texto. Los cambios se aplican al instante y se recuerdan en este dispositivo.</SettingsSectionHeader>

    <YStack gap={8}>
      <SettingsGroupLabel icon={<SunMoon color={theme.placeholder} size={14} />} title="Tema">«Sistema» cambia solo cuando cambia el tema del teléfono.</SettingsGroupLabel>
      <XStack gap={10}>
      {THEME_OPTIONS.map((option) => {
        const active = preferences.theme === option.value;
        return <NativeRipplePressable
          accessibilityLabel={`Tema ${option.label.toLowerCase()}`}
          accessibilityRole="radio"
          accessibilityState={{ selected: active }}
          key={option.value}
          onPress={() => void updatePreferences({ theme: option.value })}
          style={{ backgroundColor: active ? theme.accentSoft : theme.backgroundElement, borderColor: active ? theme.accent : theme.border, borderRadius: 14, borderWidth: 1, flex: 1, overflow: 'hidden' }}
        >
          <YStack height={72} overflow="hidden">
            <ThemePreview option={option.value} />
          </YStack>
          <Text color={active ? theme.accent : theme.text} fontSize={scale(12)} fontWeight={active ? '700' : '500'} paddingVertical={8} textAlign="center">{option.label}</Text>
          {active ? <YStack alignItems="center" backgroundColor={theme.accent} borderRadius={9} height={18} justifyContent="center" position="absolute" right={6} top={6} width={18}><Check color={theme.onAccent} size={12} /></YStack> : null}
        </NativeRipplePressable>;
      })}
      </XStack>
    </YStack>

    <YStack gap={8}>
      <SettingsGroupLabel icon={<ALargeSmall color={theme.placeholder} size={14} />} title="Tamaño del texto">Escala el texto de mensajes, listas y fichas.</SettingsGroupLabel>
      <XStack gap={10}>
        {FONT_OPTIONS.map((option) => {
          const active = preferences.fontSize === option.value;
          return <NativeRipplePressable
            accessibilityLabel={`Texto ${option.label.toLowerCase()}`}
            accessibilityRole="radio"
            accessibilityState={{ selected: active }}
            key={option.value}
            onPress={() => void updatePreferences({ fontSize: option.value })}
            style={{ alignItems: 'center', backgroundColor: active ? theme.accentSoft : theme.backgroundElement, borderColor: active ? theme.accent : theme.border, borderRadius: 14, borderWidth: 1, flex: 1, gap: 2, justifyContent: 'center', minHeight: 84, overflow: 'hidden', paddingVertical: 10 }}
          >
            <SizableText color={active ? theme.accent : theme.text} fontSize={option.size} fontWeight="700">Aa</SizableText>
            <Text color={active ? theme.accent : theme.placeholder} fontSize={scale(11)}>{option.label}</Text>
          </NativeRipplePressable>;
        })}
      </XStack>
      <Paragraph color={theme.placeholder} fontSize={scale(11)}>{describeFontScale(preferences.fontSize)}</Paragraph>
    </YStack>
  </YStack>;
}
