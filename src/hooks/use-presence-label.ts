import { useEffect, useState } from 'react';

import { describePresence } from '@/domain/presence';
import { usePreferences } from '@/hooks/use-preferences';

const TICK_MS = 30_000;

export function usePresenceLabel(lastSeen: number | undefined): string | null {
  const preferences = usePreferences();
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), TICK_MS);
    return () => clearInterval(timer);
  }, []);
  if (!lastSeen) return null;
  return describePresence({
    lastSeen,
    now,
    onlineStatus: preferences.onlineStatus,
    lastSeenEnabled: preferences.lastSeen,
  });
}
