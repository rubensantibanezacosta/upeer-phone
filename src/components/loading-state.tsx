import { ActivityIndicator, StyleSheet, View } from 'react-native';
import { Text, YStack } from 'tamagui';

import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

type LoadingStateProps = {
  label: string;
  compact?: boolean;
};

export function LoadingState({ label, compact = false }: LoadingStateProps) {
  const theme = useTheme();
  const scale = useTextScale();
  return (
    <YStack alignItems="center" flex={compact ? 0 : 1} gap={12} justifyContent="center" padding={compact ? 18 : 32}>
      <View style={styles.spinner}>
        <ActivityIndicator color={theme.accent} size={compact ? 'small' : 'large'} />
      </View>
      <Text color={theme.placeholder} fontSize={scale(13)} textAlign="center">{label}</Text>
    </YStack>
  );
}

const styles = StyleSheet.create({
  spinner: { alignItems: 'center', justifyContent: 'center', minHeight: 24, minWidth: 24 },
});
