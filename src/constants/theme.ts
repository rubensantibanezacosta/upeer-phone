/**
 * Below are the colors that are used in the app. The colors are defined in the light and dark mode.
 * There are many other ways to style your app. For example, [Nativewind](https://www.nativewind.dev/), [Tamagui](https://tamagui.dev/), [unistyles](https://unistyl.es/), etc.
 */

import '@/global.css';

import { Platform } from 'react-native';

export const Colors = {
  light: {
    text: '#000000',
    background: '#ffffff',
    screen: '#F5F5F7',
    backgroundElement: '#F0F0F3',
    backgroundSelected: '#E0E1E6',
    textSecondary: '#60646C',
    border: '#E0E1E6',
    placeholder: '#60646C',
    accent: '#0B6BCB',
    accentSoft: '#E7F0FB',
    onAccentSoft: '#0B4F99',
    onAccent: '#FFFFFF',
    success: '#1F7A3D',
    successSoft: '#E6F4EA',
    danger: '#D93036',
    dangerSoft: '#FDECEC',
    dangerBorder: '#F3C4C4',
    onDangerSoft: '#8C1D18',
    qrSurface: '#FFFFFF',
    backdrop: '#0000008C',
    bubbleMine: '#E1EFFF',
    bubbleMineText: '#0B4F99',
    receiptRead: '#0B6BCB',
    ripple: '#0000001F',
  },
  dark: {
    text: '#ffffff',
    background: '#000000',
    screen: '#202124',
    backgroundElement: '#212225',
    backgroundSelected: '#2E3135',
    textSecondary: '#B0B4BA',
    border: '#54515a',
    placeholder: '#B0B4BA',
    accent: '#0B6BCB',
    accentSoft: '#13293F',
    onAccentSoft: '#8FCBF7',
    onAccent: '#FFFFFF',
    success: '#6FD394',
    successSoft: '#12291B',
    danger: '#FF6B70',
    dangerSoft: '#2A1315',
    dangerBorder: '#5C2A2C',
    onDangerSoft: '#FFD5D3',
    qrSurface: '#FFFFFF',
    backdrop: '#000000B3',
    bubbleMine: '#13293F',
    bubbleMineText: '#D7E9FF',
    receiptRead: '#53BDEB',
    ripple: '#FFFFFF30',
  },
} as const;

export type ThemeColor = keyof typeof Colors.light & keyof typeof Colors.dark;

export const Fonts = Platform.select({
  ios: {
    /** iOS `UIFontDescriptorSystemDesignDefault` */
    sans: 'system-ui',
    /** iOS `UIFontDescriptorSystemDesignSerif` */
    serif: 'ui-serif',
    /** iOS `UIFontDescriptorSystemDesignRounded` */
    rounded: 'ui-rounded',
    /** iOS `UIFontDescriptorSystemDesignMonospaced` */
    mono: 'ui-monospace',
  },
  default: {
    sans: 'normal',
    serif: 'serif',
    rounded: 'normal',
    mono: 'monospace',
  },
  web: {
    sans: 'var(--font-display)',
    serif: 'var(--font-serif)',
    rounded: 'var(--font-rounded)',
    mono: 'var(--font-mono)',
  },
});

export const Spacing = {
  half: 2,
  one: 4,
  two: 8,
  three: 16,
  four: 24,
  five: 32,
  six: 64,
} as const;

export const BottomTabInset = Platform.select({ ios: 50, android: 80 }) ?? 0;
export const MaxContentWidth = 800;
