import { HardDrive } from '@tamagui/lucide-icons-2';
import { Paragraph, SizableText, XStack, YStack } from 'tamagui';

import { SettingsGroup, SettingsSectionHeader } from '@/components/settings-section-parts';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

export function StorageSettingsSection() {
  const theme = useTheme();
  const scale = useTextScale();

  return <YStack gap={12}>
    <SettingsSectionHeader icon={<HardDrive color={theme.accent} size={18} />} title="Almacenamiento">Los mensajes y archivos se guardan en este dispositivo, no en un servidor.</SettingsSectionHeader>

    <SettingsGroup>
      <YStack gap={6} padding={16}>
        <XStack alignItems="center" gap={8}>
          <HardDrive color={theme.placeholder} size={16} />
          <SizableText color={theme.text} flex={1} fontSize={scale(14)} fontWeight="600">Todavía no puedes gestionarlo desde aquí</SizableText>
        </XStack>
        <Paragraph color={theme.placeholder} fontSize={scale(12)}>Estamos preparando el uso de disco, la descarga de archivos y la limpieza de conversaciones. Mientras tanto, los datos se guardan solos en el almacén local y no se envían a nadie.</Paragraph>
      </YStack>
    </SettingsGroup>
  </YStack>;
}
