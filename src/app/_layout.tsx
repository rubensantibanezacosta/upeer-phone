import { DarkTheme, DefaultTheme, Stack, ThemeProvider } from 'expo-router';
import { useEffect } from 'react';
import { TamaguiProvider, createTamagui } from 'tamagui';
import { defaultConfig } from '@tamagui/config/v6';

import { BiometricLockGate } from '@/components/biometric-lock-gate';
import { SnackbarProvider } from '@/components/snackbar';
import { ThemeChrome } from '@/components/theme-chrome';
import { useResolvedColorScheme } from '@/hooks/use-resolved-color-scheme';
import { loadRecentEmojis } from '@/storage/recent-emojis-store';

const tamaguiConfig = createTamagui(defaultConfig);

export default function RootLayout() {
  const colorScheme = useResolvedColorScheme();
  useEffect(() => {
    void loadRecentEmojis();
  }, []);
  return (
    <ThemeProvider value={colorScheme === 'dark' ? DarkTheme : DefaultTheme}>
      <TamaguiProvider config={tamaguiConfig} defaultTheme={colorScheme === 'dark' ? 'dark' : 'light'}>
        <SnackbarProvider>
          <ThemeChrome />
          <Stack screenOptions={{ headerShown: false }} />
          <BiometricLockGate />
        </SnackbarProvider>
      </TamaguiProvider>
    </ThemeProvider>
  );
}
