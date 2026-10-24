import { useColorScheme as useSystemColorScheme } from 'react-native';

import { usePreferences } from '@/hooks/use-preferences';

export function useResolvedColorScheme(): 'light' | 'dark' {
  const system = useSystemColorScheme();
  const { theme } = usePreferences();
  if (theme !== 'system') return theme;
  return system === 'dark' ? 'dark' : 'light';
}
