import { useCallback } from 'react';
import * as Clipboard from 'expo-clipboard';

import { useSnackbar } from '@/components/snackbar';

export function useCopyToClipboard() {
  const { showSnackbar } = useSnackbar();

  return useCallback(async (value: string, successMessage = 'Copiado al portapapeles'): Promise<boolean> => {
    try {
      await Clipboard.setStringAsync(value);
      showSnackbar(successMessage);
      return true;
    } catch {
      showSnackbar('No se pudo copiar al portapapeles');
      return false;
    }
  }, [showSnackbar]);
}
