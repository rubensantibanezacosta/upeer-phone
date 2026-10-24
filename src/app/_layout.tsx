import { DarkTheme, DefaultTheme, Stack, ThemeProvider } from 'expo-router';
import { TamaguiProvider, createTamagui } from 'tamagui';
import { defaultConfig } from '@tamagui/config/v6';

import { SnackbarProvider } from '@/components/snackbar';
import { ThemeChrome } from '@/components/theme-chrome';
import { useResolvedColorScheme } from '@/hooks/use-resolved-color-scheme';

const tamaguiConfig = createTamagui(defaultConfig);

export default function RootLayout() {
  const colorScheme = useResolvedColorScheme();
  return (
    <ThemeProvider value={colorScheme === 'dark' ? DarkTheme : DefaultTheme}>
      <TamaguiProvider config={tamaguiConfig} defaultTheme={colorScheme === 'dark' ? 'dark' : 'light'}>
        <SnackbarProvider>
          <ThemeChrome />
          <Stack screenOptions={{ headerShown: false }} />
        </SnackbarProvider>
      </TamaguiProvider>
    </ThemeProvider>
  );
}
