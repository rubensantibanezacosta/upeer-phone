import { useCallback, useEffect, useSyncExternalStore } from 'react';

import { areRecentEmojisLoaded, getRecentEmojis, loadRecentEmojis, rememberEmoji, subscribeRecentEmojis } from '@/storage/recent-emojis-store';

export function useRecentEmojis(): { recent: string[]; ready: boolean; remember: (emoji: string) => void } {
  const recent = useSyncExternalStore(subscribeRecentEmojis, getRecentEmojis, getRecentEmojis);
  const ready = useSyncExternalStore(subscribeRecentEmojis, areRecentEmojisLoaded, areRecentEmojisLoaded);
  useEffect(() => {
    void loadRecentEmojis();
  }, []);
  const remember = useCallback((emoji: string) => {
    void rememberEmoji(emoji);
  }, []);
  return { recent, ready, remember };
}
