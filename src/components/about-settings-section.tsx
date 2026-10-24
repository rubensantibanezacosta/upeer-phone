import Constants from 'expo-constants';
import { Card, Paragraph, SizableText, YStack } from 'tamagui';

import { SettingsInfoRow } from '@/components/settings-rows';
import { useTheme } from '@/hooks/use-theme';

export function AboutSettingsSection() {
  const theme = useTheme();
  const version = Constants.expoConfig?.version ?? '1.0.0';

  return <YStack gap={12}>
    <Card backgroundColor={theme.backgroundElement} borderColor={theme.border} borderRadius={14} borderWidth={1} gap={6} padding={16}>
      <SizableText color={theme.text} fontSize={22} fontWeight="700">Acerca de</SizableText>
      <Paragraph color={theme.placeholder} fontSize={13}>uPeer funciona sin servidores centrales: los mensajes viajan cifrados de extremo a extremo entre dispositivos.</Paragraph>
    </Card>

    <YStack borderColor={theme.border} borderRadius={14} borderWidth={1} overflow="hidden">
      <SettingsInfoRow label="Versión" value={version} />
      <SettingsInfoRow label="Licencia" value="GPLv3" />
      <SettingsInfoRow label="Código abierto" value="Sí" />
      <SettingsInfoRow label="Publicidad" value="Ninguna" />
      <SettingsInfoRow label="Datos recopilados" value="Ninguno" />
    </YStack>
  </YStack>;
}
