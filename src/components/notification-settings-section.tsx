import { Bell } from '@tamagui/lucide-icons-2';
import { YStack } from 'tamagui';

import { SettingsGroup, SettingsNote, SettingsSectionHeader } from '@/components/settings-section-parts';
import { SettingsToggleRow } from '@/components/settings-rows';
import { usePreferences } from '@/hooks/use-preferences';
import { useTheme } from '@/hooks/use-theme';
import { updatePreferences } from '@/storage/preferences-store';

export function NotificationSettingsSection() {
  const theme = useTheme();
  const preferences = usePreferences();

  return <YStack gap={12}>
    <SettingsSectionHeader icon={<Bell color={theme.accent} size={18} />} title="Notificaciones">Avisos que llegan mientras la app está en segundo plano. La previsualización del mensaje ya es privada.</SettingsSectionHeader>

    <SettingsGroup>
      <SettingsToggleRow
        description="Avisar cuando recibes un mensaje"
        label="Nuevos mensajes"
        onChange={(value) => void updatePreferences({ messageNotifications: value })}
        value={preferences.messageNotifications}
      />
      <SettingsToggleRow
        description="Avisar cuando alguien quiere contactarte"
        label="Solicitudes de contacto"
        onChange={(value) => void updatePreferences({ requestNotifications: value })}
        value={preferences.requestNotifications}
      />
      <SettingsToggleRow
        description="Reproducir sonidos de notificación"
        label="Sonidos"
        onChange={(value) => void updatePreferences({ notificationSound: value })}
        value={preferences.notificationSound}
      />
    </SettingsGroup>

    <SettingsNote>Se guardan en este dispositivo y se envían al servicio, que ya omite el aviso y el sonido según estas preferencias; el canal de mensajes respeta el permiso del sistema.</SettingsNote>
  </YStack>;
}
