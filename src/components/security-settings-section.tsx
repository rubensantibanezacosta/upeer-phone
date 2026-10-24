import { useState } from 'react';
import { Card, Paragraph, SizableText, YStack } from 'tamagui';

import { DeviceAuthorizerPanel } from '@/components/device-authorizer-panel';
import { InfoDialog } from '@/components/info-dialog';
import { profileHelpContent } from '@/components/profile-help-content';
import { useTheme } from '@/hooks/use-theme';

export function SecuritySettingsSection() {
  const theme = useTheme();
  const [infoOpen, setInfoOpen] = useState(false);

  return <YStack gap={12}>
    <Card backgroundColor={theme.backgroundElement} borderColor={theme.border} borderRadius={14} borderWidth={1} gap={6} padding={16}>
      <SizableText color={theme.text} fontSize={22} fontWeight="700">Seguridad</SizableText>
      <Paragraph color={theme.placeholder} fontSize={13}>Claves de la cuenta y dispositivos con acceso. Revocar uno le quita el acceso sin tocar el resto.</Paragraph>
    </Card>

    <YStack borderColor={theme.border} borderRadius={14} borderWidth={1} overflow="hidden">
      <DeviceAuthorizerPanel onInfo={() => setInfoOpen(true)} />
    </YStack>

    <InfoDialog content={infoOpen ? profileHelpContent.dispositivos : null} onClose={() => setInfoOpen(false)} />
  </YStack>;
}
