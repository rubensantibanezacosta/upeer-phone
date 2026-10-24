import Constants from 'expo-constants';
import { YStack } from 'tamagui';

import { SettingsSectionHeader } from '@/components/settings-section-parts';
import { SettingsInfoRow } from '@/components/settings-rows';
import { useTheme } from '@/hooks/use-theme';

export function AboutSettingsSection() {
  const theme = useTheme();
  const version = Constants.expoConfig?.version ?? '1.0.0';

  return <YStack gap={12}>
    <SettingsSectionHeader title="Acerca de">uPeer funciona sin servidores centrales: los mensajes viajan cifrados de extremo a extremo entre dispositivos.</SettingsSectionHeader>

    <YStack borderColor={theme.border} borderRadius={14} borderWidth={1} overflow="hidden">
      <SettingsInfoRow label="Versión" value={version} />
      <SettingsInfoRow label="Licencia" value="GPLv3" />
      <SettingsInfoRow label="Código abierto" value="Sí" />
      <SettingsInfoRow label="Publicidad" value="Ninguna" />
      <SettingsInfoRow label="Datos recopilados" value="Ninguno" />
    </YStack>
  </YStack>;
}
