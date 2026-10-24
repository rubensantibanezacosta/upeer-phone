import { YStack } from 'tamagui';

import { SettingsNote, SettingsSectionHeader } from '@/components/settings-section-parts';
import { SettingsToggleRow } from '@/components/settings-rows';
import { usePreferences } from '@/hooks/use-preferences';
import { useTheme } from '@/hooks/use-theme';
import { updatePreferences } from '@/storage/preferences-store';

export function PrivacySettingsSection() {
  const theme = useTheme();
  const preferences = usePreferences();

  return <YStack gap={12}>
    <SettingsSectionHeader title="Privacidad">Qué comparte la app sobre tu actividad. Si desactivas algo, tus contactos dejan de verlo.</SettingsSectionHeader>

    <YStack borderColor={theme.border} borderRadius={14} borderWidth={1} overflow="hidden">
      <SettingsToggleRow
        description="Enviar y recibir confirmaciones de leído"
        label="Confirmaciones de lectura"
        onChange={(value) => void updatePreferences({ readReceipts: value })}
        value={preferences.readReceipts}
      />
      <SettingsToggleRow
        description="Que tus contactos vean si estás disponible"
        label="Mostrar cuando estoy conectado"
        onChange={(value) => void updatePreferences({ onlineStatus: value })}
        value={preferences.onlineStatus}
      />
      <SettingsToggleRow
        description="Mostrar tu última actividad a tus contactos"
        label="Última vez visto"
        onChange={(value) => void updatePreferences({ lastSeen: value })}
        value={preferences.lastSeen}
      />
    </YStack>

    <SettingsNote>Las confirmaciones de lectura ya se aplican en el servicio de red: al desactivarlas, la conversación se marca como leída solo en este dispositivo y no se envía el mensaje `READ`. Conectado y última vez usan la presencia real que ya viaja con los paquetes.</SettingsNote>
  </YStack>;
}
