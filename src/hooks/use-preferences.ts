import { useEffect, useSyncExternalStore } from 'react';

import type { Preferences } from '@/domain/preferences';
import { getPreferences, loadPreferences, subscribePreferences } from '@/storage/preferences-store';

export function usePreferences(): Preferences {
  const preferences = useSyncExternalStore(subscribePreferences, getPreferences, getPreferences);
  useEffect(() => {
    void loadPreferences();
  }, []);
  return preferences;
}
