import { describePresence } from '@/domain/presence';
import { useNow } from '@/hooks/use-now';
import { usePreferences } from '@/hooks/use-preferences';

export function usePresenceLabel(lastActive: number | undefined, lastSeen?: number): string | null {
  const preferences = usePreferences();
  const now = useNow();
  if (!lastActive && !lastSeen) return null;
  return describePresence({
    lastActive,
    lastSeen,
    now,
    onlineStatus: preferences.onlineStatus,
    lastSeenEnabled: preferences.lastSeen,
  });
}
