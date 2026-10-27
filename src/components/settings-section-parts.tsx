import { Children, type ReactNode } from 'react';
import { StyleSheet, View } from 'react-native';
import { Paragraph, SizableText, YStack } from 'tamagui';

import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

type SettingsSectionHeaderProps = {
  action?: ReactNode;
  children: ReactNode;
  icon?: ReactNode;
  title: string;
};

export function SettingsSectionHeader({ action, children, icon, title }: SettingsSectionHeaderProps) {
  const theme = useTheme();
  const scale = useTextScale();
  return (
    <YStack alignItems="flex-start" flexDirection="row" gap={12}>
      {icon ? <YStack alignItems="center" backgroundColor={theme.accentSoft} borderRadius={10} height={34} justifyContent="center" width={34}>{icon}</YStack> : null}
      <YStack flex={1} gap={4}>
        <SizableText color={theme.text} fontSize={scale(20)} fontWeight="700">{title}</SizableText>
        <Paragraph color={theme.placeholder} fontSize={scale(13)}>{children}</Paragraph>
      </YStack>
      {action}
    </YStack>
  );
}

export function SettingsGroupLabel({ children, icon, title }: { children?: ReactNode; icon?: ReactNode; title: string }) {
  const theme = useTheme();
  const scale = useTextScale();
  return (
    <YStack gap={2}>
      <YStack alignItems="center" flexDirection="row" gap={8}>
        {icon}
        <SizableText color={theme.text} fontSize={scale(15)} fontWeight="700">{title}</SizableText>
      </YStack>
      {children ? <Paragraph color={theme.placeholder} fontSize={scale(12)}>{children}</Paragraph> : null}
    </YStack>
  );
}

export function SettingsGroup({ children }: { children: ReactNode }) {
  const theme = useTheme();
  return (
    <YStack backgroundColor={theme.backgroundElement} borderRadius={14} overflow="hidden">
      {Children.toArray(children).map((child, index) => (
        <View key={index} style={index === 0 ? undefined : [styles.separator, { borderTopColor: theme.border }]}>{child}</View>
      ))}
    </YStack>
  );
}

export function SettingsNote({ children }: { children: ReactNode }) {
  const theme = useTheme();
  const scale = useTextScale();
  return <Paragraph color={theme.placeholder} fontSize={scale(12)}>{children}</Paragraph>;
}

const styles = StyleSheet.create({
  separator: { borderTopWidth: StyleSheet.hairlineWidth },
});
