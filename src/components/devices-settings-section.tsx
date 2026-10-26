import { useState } from 'react';
import { Smartphone } from '@tamagui/lucide-icons-2';
import { YStack } from 'tamagui';

import { DeviceAuthorizerPanel } from '@/components/device-authorizer-panel';
import { InfoDialog } from '@/components/info-dialog';
import { profileHelpContent } from '@/components/profile-help-content';
import { SettingsGroup, SettingsSectionHeader } from '@/components/settings-section-parts';
import { useTheme } from '@/hooks/use-theme';

export function DevicesSettingsSection() {
  const theme = useTheme();
  const [infoOpen, setInfoOpen] = useState(false);

  return <YStack gap={12}>
    <SettingsSectionHeader icon={<Smartphone color={theme.accent} size={18} />} title="Dispositivos">Los dispositivos vinculados comparten la misma cuenta, los contactos y las conversaciones. Ninguno recibe la frase de recuperación.</SettingsSectionHeader>

    <SettingsGroup>
      <DeviceAuthorizerPanel onInfo={() => setInfoOpen(true)} />
    </SettingsGroup>

    <InfoDialog content={infoOpen ? profileHelpContent.dispositivos : null} onClose={() => setInfoOpen(false)} />
  </YStack>;
}
