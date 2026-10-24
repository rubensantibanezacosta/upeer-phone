import { useCallback } from 'react';

import { scaleFontSize } from '@/domain/text-scale';
import { usePreferences } from '@/hooks/use-preferences';

export function useTextScale(): (size: number) => number {
  const { fontSize } = usePreferences();
  return useCallback((size: number) => scaleFontSize(size, fontSize), [fontSize]);
}
