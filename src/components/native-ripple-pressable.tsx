import type { ComponentProps } from 'react';
import { Pressable, type PressableProps } from 'react-native';

import { useTheme } from '@/hooks/use-theme';

type NativeRipplePressableProps = PressableProps & { ref?: ComponentProps<typeof Pressable>['ref'] };

export function NativeRipplePressable({ android_ripple, ...props }: NativeRipplePressableProps) {
  const theme = useTheme();
  return <Pressable {...props} android_ripple={android_ripple ?? { borderless: false, color: theme.ripple }} />;
}
