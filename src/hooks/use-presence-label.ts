import { describePresence } from '@/domain/presence';
import { useNow } from '@/hooks/use-now';
import { usePreferences } from '@/hooks/use-preferences';

export function usePresenceLabel(lastSeen: number | undefined): string | null {
  const preferences = usePreferences();
  const now = useNow();
  if (!lastSeen) return null;
  return describePresence({
    lastSeen,
    now,
    onlineStatus: preferences.onlineStatus,
    lastSeenEnabled: preferences.lastSeen,
  });
}
