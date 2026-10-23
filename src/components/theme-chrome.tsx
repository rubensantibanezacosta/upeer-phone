import { useEffect } from 'react';
import { Platform } from 'react-native';
import { StatusBar } from 'expo-status-bar';
import * as SystemUI from 'expo-system-ui';
import * as NavigationBar from 'expo-navigation-bar';

import { useTheme } from '@/hooks/use-theme';
import { useColorScheme } from '@/hooks/use-color-scheme';

function applyNavigationBar(dark: boolean) {
  if (Platform.OS !== 'android') return;
  try {
    NavigationBar.setStyle(dark ? 'light' : 'dark');
  } catch (cause: unknown) {
    console.warn('No se pudo aplicar el contraste de la barra de navegación.', cause);
  }
}

export function ThemeChrome() {
  const theme = useTheme();
  const dark = useColorScheme() === 'dark';

  useEffect(() => {
    if (Platform.OS === 'web') return;
    SystemUI.setBackgroundColorAsync(theme.background).catch((cause: unknown) => {
      console.warn('No se pudo aplicar el color de fondo del sistema.', cause);
    });
  }, [theme.background]);

  useEffect(() => {
    applyNavigationBar(dark);
  }, [dark]);

  return <StatusBar style={dark ? 'light' : 'dark'} />;
}
