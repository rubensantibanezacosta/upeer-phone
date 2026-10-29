import { describePresence } from '@/domain/presence';
import { useNow } from '@/hooks/use-now';
import { usePreferences } from '@/hooks/use-preferences';

export function usePresenceLabel(lastActive: number | undefined): string | null {
  const preferences = usePreferences();
  const now = useNow();
  if (!lastActive) return null;
  return describePresence({ lastActive, now, shareActivity: preferences.shareActivity });
}
