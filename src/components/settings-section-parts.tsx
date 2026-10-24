import type { ReactNode } from 'react';
import { Card, Paragraph, SizableText } from 'tamagui';

import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

export function SettingsSectionHeader({ children, title }: { children: ReactNode; title: string }) {
  const theme = useTheme();
  const scale = useTextScale();
  return (
    <Card backgroundColor={theme.backgroundElement} borderColor={theme.border} borderRadius={14} borderWidth={1} gap={6} padding={16}>
      <SizableText color={theme.text} fontSize={scale(22)} fontWeight="700">{title}</SizableText>
      <Paragraph color={theme.placeholder} fontSize={scale(13)}>{children}</Paragraph>
    </Card>
  );
}

export function SettingsNote({ children }: { children: ReactNode }) {
  const theme = useTheme();
  const scale = useTextScale();
  return <Paragraph color={theme.placeholder} fontSize={scale(12)}>{children}</Paragraph>;
}
