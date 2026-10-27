import { useCallback, useEffect, useRef, useState } from 'react';
import type { FlatList, NativeScrollEvent, NativeSyntheticEvent } from 'react-native';

import { resolveChatAnchor } from '@/domain/chat-anchor';
import type { ChatTimelineEntry } from '@/domain/chat-timeline';

const LATEST_OFFSET = 24;
const SCROLL_BUTTON_OFFSET = 180;

type UseChatTimelineScrollOptions = {
  listRef: React.RefObject<FlatList<ChatTimelineEntry> | null>;
  messageCount: number;
  onMissingQuoted: () => void;
  timeline: ChatTimelineEntry[];
};

export function useChatTimelineScroll({ listRef, messageCount, onMissingQuoted, timeline }: UseChatTimelineScrollOptions) {
  const atLatestRef = useRef(true);
  const previousCountRef = useRef(0);
  const [showScrollButton, setShowScrollButton] = useState(false);

  useEffect(() => {
    const previousCount = previousCountRef.current;
    previousCountRef.current = messageCount;
    if (messageCount === 0 || messageCount <= previousCount) return;
    if (!atLatestRef.current && !timeline[0]?.message.isMine) return;
    listRef.current?.scrollToOffset({ animated: false, offset: 0 });
    setShowScrollButton(false);
  }, [listRef, messageCount, timeline]);

  const handleScroll = useCallback((event: NativeSyntheticEvent<NativeScrollEvent>) => {
    const offset = Math.max(0, event.nativeEvent.contentOffset.y);
    atLatestRef.current = offset < LATEST_OFFSET;
    setShowScrollButton(offset > SCROLL_BUTTON_OFFSET);
  }, []);

  const scrollToLatest = useCallback(() => {
    atLatestRef.current = true;
    setShowScrollButton(false);
    listRef.current?.scrollToOffset({ animated: true, offset: 0 });
  }, [listRef]);

  const scrollToMessage = useCallback((messageID: string) => {
    const anchor = resolveChatAnchor(timeline, messageID);
    if (!anchor) {
      onMissingQuoted();
      return;
    }
    atLatestRef.current = anchor.index === 0;
    listRef.current?.scrollToIndex({ animated: true, index: anchor.index, viewPosition: anchor.viewPosition });
  }, [listRef, onMissingQuoted, timeline]);

  return { handleScroll, scrollToLatest, scrollToMessage, showScrollButton };
}
