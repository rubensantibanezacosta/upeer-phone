import { useEffect, useSyncExternalStore } from 'react';

import { getRecentEmojis, loadRecentEmojis, rememberEmoji, subscribeRecentEmojis } from '@/storage/recent-emojis-store';

export function useRecentEmojis(): { recent: string[]; remember: (emoji: string) => void } {
  const recent = useSyncExternalStore(subscribeRecentEmojis, getRecentEmojis, getRecentEmojis);
  useEffect(() => {
    void loadRecentEmojis();
  }, []);
  return { recent, remember: (emoji: string) => void rememberEmoji(emoji) };
}
