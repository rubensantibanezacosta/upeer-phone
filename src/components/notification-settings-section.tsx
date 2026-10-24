import { Card, Paragraph, SizableText, YStack } from 'tamagui';

import { SettingsToggleRow } from '@/components/settings-rows';
import { usePreferences } from '@/hooks/use-preferences';
import { useTheme } from '@/hooks/use-theme';
import { updatePreferences } from '@/storage/preferences-store';

export function NotificationSettingsSection() {
  const theme = useTheme();
  const preferences = usePreferences();

  return <YStack gap={12}>
    <Card backgroundColor={theme.backgroundElement} borderColor={theme.border} borderRadius={14} borderWidth={1} gap={6} padding={16}>
      <SizableText color={theme.text} fontSize={22} fontWeight="700">Notificaciones</SizableText>
      <Paragraph color={theme.placeholder} fontSize={13}>Avisos que llegan mientras la app está en segundo plano. La previsualización del mensaje ya es privada.</Paragraph>
    </Card>

    <YStack borderColor={theme.border} borderRadius={14} borderWidth={1} overflow="hidden">
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
    </YStack>

    <Paragraph color={theme.placeholder} fontSize={12}>Se guardan en este dispositivo y se enviarán al servicio con el próximo trabajo de notificaciones; el canal de mensajes ya existe y respeta el permiso del sistema.</Paragraph>
  </YStack>;
}
