import type { ComponentType, ReactNode } from 'react';
import { StyleSheet } from 'react-native';
import { ChevronRight } from '@tamagui/lucide-icons-2';
import { SizableText, Text, YStack } from 'tamagui';

import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

type IconComponent = ComponentType<{ color?: string; size?: number }>;

type InteractiveRowProps = {
  icon: IconComponent;
  label: string;
  onPress: () => void;
  accessibilityLabel?: string;
  disabled?: boolean;
  tone?: 'default' | 'danger';
  hint?: string;
  description?: string;
  variant?: 'row' | 'action';
  chevron?: boolean;
  trailing?: ReactNode;
};

export function InteractiveRow({ icon: Icon, label, onPress, accessibilityLabel, disabled = false, tone = 'default', hint, description, variant = 'row', chevron = false, trailing }: InteractiveRowProps) {
  const theme = useTheme();
  const scale = useTextScale();
  const color = tone === 'danger' ? theme.danger : theme.text;
  const labelColor = tone === 'danger' ? theme.danger : theme.text;
  const dimmed = disabled ? styles.dimmed : null;

  if (variant === 'action') {
    return (
      <NativeRipplePressable accessibilityLabel={accessibilityLabel ?? label} accessibilityRole="button" accessibilityState={{ disabled }} disabled={disabled} onPress={onPress} style={[styles.action, dimmed]}>
        <YStack alignItems="center" backgroundColor={theme.backgroundElement} borderRadius={26} height={52} justifyContent="center" width={52}>
          <Icon color={color} size={22} />
        </YStack>
        <Text color={labelColor} fontSize={scale(11)} fontWeight="600" numberOfLines={1}>{label}</Text>
      </NativeRipplePressable>
    );
  }

  return (
    <NativeRipplePressable accessibilityLabel={accessibilityLabel ?? label} accessibilityRole="button" accessibilityState={{ disabled }} disabled={disabled} onPress={onPress} style={[styles.row, dimmed]}>
      <Icon color={color} size={22} />
      <YStack flex={1} gap={3}>
        <SizableText color={labelColor} fontSize={scale(15)} fontWeight="600">{label}</SizableText>
        {description ? <Text color={theme.placeholder} fontSize={scale(12)}>{description}</Text> : null}
      </YStack>
      {trailing ?? (hint ? <Text color={theme.placeholder} fontSize={scale(11)} fontWeight="500">{hint}</Text> : chevron ? <ChevronRight color={theme.placeholder} size={15} /> : null)}
    </NativeRipplePressable>
  );
}

const styles = StyleSheet.create({
  action: { alignItems: 'center', borderRadius: 30, gap: 6, minWidth: 62, overflow: 'hidden', paddingVertical: 4 },
  dimmed: { opacity: 0.45 },
  row: { alignItems: 'center', flexDirection: 'row', gap: 12, minHeight: 64, overflow: 'hidden', paddingHorizontal: 16, paddingVertical: 12 },
});
