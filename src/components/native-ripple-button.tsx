import type { ComponentProps } from 'react';
import { processColor } from 'react-native';
import { Button } from 'tamagui';

import { useTheme } from '@/hooks/use-theme';

type NativeRippleButtonProps = ComponentProps<typeof Button>;

export function NativeRippleButton({ overflow = 'hidden', ...props }: NativeRippleButtonProps) {
  const theme = useTheme();
  return <Button {...props} nativeBackgroundAndroid={{ borderless: false, color: processColor(theme.ripple) ?? undefined, type: 'RippleAndroid' }} overflow={overflow} />;
}
