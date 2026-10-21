import type { ComponentProps } from 'react';
import { Pressable, type PressableProps } from 'react-native';

type NativeRipplePressableProps = PressableProps & { ref?: ComponentProps<typeof Pressable>['ref'] };

export function NativeRipplePressable({ android_ripple, ...props }: NativeRipplePressableProps) {
  return <Pressable {...props} android_ripple={android_ripple ?? { color: '#ffffff30', borderless: false }} />;
}