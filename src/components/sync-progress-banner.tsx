import { Paragraph, SizableText, YStack } from 'tamagui';

import { useSyncStatus } from '@/hooks/use-sync-status';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

export function SyncProgressBanner() {
  const theme = useTheme();
  const scale = useTextScale();
  const status = useSyncStatus();

  if (status.state === 'syncing' || status.unsupported === 0) return null;

  return (
    <YStack
      backgroundColor={theme.backgroundElement}
      borderRadius={14}
      gap={6}
      marginBottom={12}
      paddingHorizontal={14}
      paddingVertical={12}
    >
      <SizableText color={theme.text} fontSize={scale(14)} fontWeight="600">Sincronización parcial</SizableText>
      <Paragraph color={theme.placeholder} fontSize={scale(12)}>
        {`${status.unsupported} elementos sin contenido completo: los grupos y el contenido de los adjuntos aún no se replican.`}
      </Paragraph>
    </YStack>
  );
}
