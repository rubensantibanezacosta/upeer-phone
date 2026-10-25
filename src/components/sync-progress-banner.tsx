import { ActivityIndicator, View } from 'react-native';
import { Paragraph, SizableText, XStack, YStack } from 'tamagui';

import { useSyncStatus } from '@/hooks/use-sync-status';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

export function SyncProgressBanner() {
  const theme = useTheme();
  const scale = useTextScale();
  const status = useSyncStatus();

  const syncing = status.state === 'syncing';
  const gap = status.unsupported > 0;
  if (!syncing && !gap) return null;

  const total = Math.max(status.devices, status.devicesDone);
  const detail = syncing
    ? (status.pendingEvents > 0
      ? `${status.pendingEvents} eventos pendientes de aplicar`
      : `${status.devicesDone} de ${total} dispositivos al día`)
    : `${status.unsupported} elementos sin contenido completo`;

  return (
    <YStack
      backgroundColor={theme.backgroundElement}
      borderRadius={14}
      gap={6}
      marginBottom={12}
      paddingHorizontal={14}
      paddingVertical={12}
    >
      <XStack alignItems="center" gap={10}>
        {syncing ? (
          <View style={{ width: 18, height: 18 }}>
            <ActivityIndicator color={theme.accent} size="small" />
          </View>
        ) : null}
        <SizableText color={theme.text} flex={1} fontSize={scale(14)} fontWeight="600">
          {syncing ? 'Sincronizando tus dispositivos' : 'Sincronización parcial'}
        </SizableText>
      </XStack>
      <Paragraph color={theme.placeholder} fontSize={scale(12)}>
        {syncing
          ? `${detail}. Los chats aparecerán aquí en cuanto termine.`
          : `${detail}: los grupos y el contenido de los adjuntos aún no se replican.`}
      </Paragraph>
    </YStack>
  );
}
