import { DarkTheme, DefaultTheme, Stack, ThemeProvider } from 'expo-router';
import { useColorScheme } from 'react-native';
import { TamaguiProvider, createTamagui } from 'tamagui';
import { defaultConfig } from '@tamagui/config/v6';

import { SnackbarProvider } from '@/components/snackbar';
import { ThemeChrome } from '@/components/theme-chrome';

const tamaguiConfig = createTamagui(defaultConfig);

export default function RootLayout() {
  const colorScheme = useColorScheme();
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
