import { LockKeyhole } from '@tamagui/lucide-icons-2';
import { YStack } from 'tamagui';

import { SettingsGroup, SettingsNote, SettingsSectionHeader } from '@/components/settings-section-parts';
import { SettingsToggleRow } from '@/components/settings-rows';
import { usePreferences } from '@/hooks/use-preferences';
import { useTheme } from '@/hooks/use-theme';
import { updatePreferences } from '@/storage/preferences-store';

export function PrivacySettingsSection() {
  const theme = useTheme();
  const preferences = usePreferences();

  return <YStack gap={12}>
    <SettingsSectionHeader icon={<LockKeyhole color={theme.accent} size={18} />} title="Privacidad">Las dos primeras se anuncian a tus contactos; presencia y última vez solo cambian lo que ves de ellos.</SettingsSectionHeader>

    <SettingsGroup>
      <SettingsToggleRow
        description="Enviar y recibir confirmaciones de leído"
        label="Confirmaciones de lectura"
        onChange={(value) => void updatePreferences({ readReceipts: value })}
        value={preferences.readReceipts}
      />
      <SettingsToggleRow
        description="Enviar la hora de tu última actividad a tus contactos"
        label="Compartir mi hora de actividad"
        onChange={(value) => void updatePreferences({ shareActivity: value })}
        value={preferences.shareActivity}
      />
      <SettingsToggleRow
        description="Ver si están en línea en la lista y en el chat"
        label="Presencia de tus contactos"
        onChange={(value) => void updatePreferences({ onlineStatus: value })}
        value={preferences.onlineStatus}
      />
      <SettingsToggleRow
        description="Ver cuándo fue su última actividad"
        label="Última vez de tus contactos"
        onChange={(value) => void updatePreferences({ lastSeen: value })}
        value={preferences.lastSeen}
      />
    </SettingsGroup>

    <SettingsNote>Al desactivar las confirmaciones de lectura no se envía `READ`: la conversación se marca leída solo aquí. Al desactivar «Compartir mi hora de actividad» tu latido deja de llevar la hora: lo que ya vieron tus contactos no se borra, pero deja de avanzar. El punto verde de «en línea» sigue dependiendo de que la app esté abierta, porque se deduce de cualquier paquete y no solo de ese latido.</SettingsNote>
  </YStack>;
}
