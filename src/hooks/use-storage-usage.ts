import { useCallback, useEffect, useSyncExternalStore } from 'react';

import { useSnackbar } from '@/components/snackbar';
import { clearManagedCache, getStorageUsageSnapshot, refreshStorageUsage, subscribeStorageUsage } from '@/storage/storage-usage-store';

export function useStorageUsage() {
  const { showSnackbar } = useSnackbar();
  const snapshot = useSyncExternalStore(subscribeStorageUsage, getStorageUsageSnapshot, getStorageUsageSnapshot);

  useEffect(() => {
    refreshStorageUsage();
  }, []);

  const clearCache = useCallback(() => {
    try {
      clearManagedCache();
      refreshStorageUsage();
      showSnackbar('Caché liberada');
    } catch {
      showSnackbar('No se pudo limpiar la caché');
    }
  }, [showSnackbar]);

  return { clearCache, error: snapshot.error, ready: snapshot.loaded, refresh: refreshStorageUsage, usage: snapshot.usage };
}
