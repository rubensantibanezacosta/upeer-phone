import type { ComponentProps } from 'react';
import { processColor } from 'react-native';
import { Button } from 'tamagui';

type NativeRippleButtonProps = ComponentProps<typeof Button>;

export function NativeRippleButton(props: NativeRippleButtonProps) {
  return <Button {...props} nativeBackgroundAndroid={{ type: 'RippleAndroid', color: processColor('#ffffff30') ?? undefined, borderless: false }} />;
}