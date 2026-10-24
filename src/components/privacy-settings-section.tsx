import { Card, Paragraph, SizableText, YStack } from 'tamagui';

import { SettingsToggleRow } from '@/components/settings-rows';
import { usePreferences } from '@/hooks/use-preferences';
import { useTheme } from '@/hooks/use-theme';
import { updatePreferences } from '@/storage/preferences-store';

export function PrivacySettingsSection() {
  const theme = useTheme();
  const preferences = usePreferences();

  return <YStack gap={12}>
    <Card backgroundColor={theme.backgroundElement} borderColor={theme.border} borderRadius={14} borderWidth={1} gap={6} padding={16}>
      <SizableText color={theme.text} fontSize={22} fontWeight="700">Privacidad</SizableText>
      <Paragraph color={theme.placeholder} fontSize={13}>Qué comparte la app sobre tu actividad. Si desactivas algo, tus contactos dejan de verlo.</Paragraph>
    </Card>

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

    <Paragraph color={theme.placeholder} fontSize={12}>Las tres preferencias se guardan en este dispositivo. El servicio aplicará las dos últimas cuando envíe presencia; las confirmaciones de lectura llegarán con el mensaje `READ`.</Paragraph>
  </YStack>;
}
