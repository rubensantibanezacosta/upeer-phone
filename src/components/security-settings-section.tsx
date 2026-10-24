import { useState } from 'react';
import { YStack } from 'tamagui';

import { DeviceAuthorizerPanel } from '@/components/device-authorizer-panel';
import { InfoDialog } from '@/components/info-dialog';
import { profileHelpContent } from '@/components/profile-help-content';
import { SettingsSectionHeader } from '@/components/settings-section-parts';
import { useTheme } from '@/hooks/use-theme';

export function SecuritySettingsSection() {
  const theme = useTheme();
  const [infoOpen, setInfoOpen] = useState(false);

  return <YStack gap={12}>
    <SettingsSectionHeader title="Seguridad">Claves de la cuenta y dispositivos con acceso. Revocar uno le quita el acceso sin tocar el resto.</SettingsSectionHeader>

    <YStack borderColor={theme.border} borderRadius={14} borderWidth={1} overflow="hidden">
      <DeviceAuthorizerPanel onInfo={() => setInfoOpen(true)} />
    </YStack>

    <InfoDialog content={infoOpen ? profileHelpContent.dispositivos : null} onClose={() => setInfoOpen(false)} />
  </YStack>;
}
