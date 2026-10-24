import { SizableText, XStack } from 'tamagui';

import { formatBadgeCount } from '@/domain/chat-unread';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

type UnreadCountBadgeProps = {
  count: number;
};

export function UnreadCountBadge({ count }: UnreadCountBadgeProps) {
  const theme = useTheme();
  const scale = useTextScale();
  const label = formatBadgeCount(count);
  if (!label) return null;

  return (
    <XStack alignItems="center" backgroundColor={theme.accent} borderRadius={10} height={20} justifyContent="center" minWidth={20} paddingHorizontal={6}>
      <SizableText color={theme.onAccent} fontSize={scale(11)} fontWeight="700" style={styles.label}>{label}</SizableText>
    </XStack>
  );
}

const styles = { label: { includeFontPadding: false, textAlign: 'center' as const, textAlignVertical: 'center' as const, transform: [{ translateY: -1 }] } };
