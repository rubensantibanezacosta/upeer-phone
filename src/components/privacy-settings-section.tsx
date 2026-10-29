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
    <SettingsSectionHeader icon={<LockKeyhole color={theme.accent} size={18} />} title="Privacidad">Las confirmaciones de lectura se anuncian a tus contactos. Presencia y actividad van juntas: o las compartes y las ves, o ni una cosa ni la otra.</SettingsSectionHeader>

    <SettingsGroup>
      <SettingsToggleRow
        description="Enviar y recibir confirmaciones de leído"
        label="Confirmaciones de lectura"
        onChange={(value) => void updatePreferences({ readReceipts: value })}
        value={preferences.readReceipts}
      />
      <SettingsToggleRow
        description="Ver su presencia y su última actividad, y que ellos vean la tuya"
        label="Compartir mi presencia y actividad"
        onChange={(value) => void updatePreferences({ shareActivity: value })}
        value={preferences.shareActivity}
      />
    </SettingsGroup>

    <SettingsNote>Al desactivar las confirmaciones de lectura no se envía `READ`: la conversación se marca leída solo aquí. Al desactivar «Compartir mi presencia y actividad» tu latido deja de llevar la hora, dejas de ver la presencia y la última actividad de tus contactos, y ellos dejan de ver la tuya. El punto verde de «en línea» depende de que la app esté abierta.</SettingsNote>
  </YStack>;
}
