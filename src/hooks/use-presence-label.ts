import { describePresence } from '@/domain/presence';
import { useNow } from '@/hooks/use-now';
import { usePreferences } from '@/hooks/use-preferences';

export function usePresenceLabel(lastActive: number | undefined): string | null {
  const preferences = usePreferences();
  const now = useNow();
  return describePresence({ lastActive, now, shareActivity: preferences.shareActivity });
}
