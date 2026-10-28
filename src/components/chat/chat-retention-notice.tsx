import { Timer } from '@tamagui/lucide-icons-2';
import { Text, XStack } from 'tamagui';

import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

type ChatRetentionNoticeProps = {
  text?: string | null;
};

export function ChatRetentionNotice({ text }: ChatRetentionNoticeProps) {
  const theme = useTheme();
  const scale = useTextScale();
  if (!text) return null;

  return (
    <XStack alignItems="center" alignSelf="center" gap={6} paddingVertical={8}>
      <Timer color={theme.placeholder} size={13} />
      <Text color={theme.placeholder} fontSize={scale(11)}>{text}</Text>
    </XStack>
  );
}
