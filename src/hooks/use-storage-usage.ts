import { useEffect, useSyncExternalStore } from 'react';

import { getStorageUsageSnapshot, refreshStorageUsage, subscribeStorageUsage } from '@/storage/storage-usage-store';

export function useStorageUsage() {
  const snapshot = useSyncExternalStore(subscribeStorageUsage, getStorageUsageSnapshot, getStorageUsageSnapshot);

  useEffect(() => {
    refreshStorageUsage();
  }, []);

  return { error: snapshot.error, ready: snapshot.loaded, refresh: refreshStorageUsage, usage: snapshot.usage };
}
