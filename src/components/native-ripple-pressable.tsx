import type { ComponentProps } from 'react';
import { Pressable, StyleSheet, type PressableProps, type ViewStyle } from 'react-native';

import { circularRippleRadius } from '@/domain/ripple-radius';
import { useTheme } from '@/hooks/use-theme';

type NativeRipplePressableProps = PressableProps & { ref?: ComponentProps<typeof Pressable>['ref'] };

export function NativeRipplePressable({ android_ripple, style, ...props }: NativeRipplePressableProps) {
  const theme = useTheme();
  const radius = typeof style === 'function' ? undefined : circularRippleRadius(StyleSheet.flatten(style) as ViewStyle | undefined);
  return (
    <Pressable
      {...props}
      android_ripple={android_ripple ?? { borderless: false, color: theme.ripple, radius }}
      style={style}
    />
  );
}
