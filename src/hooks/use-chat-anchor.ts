import { useCallback, useEffect, useRef, useState } from 'react';
import type { FlatList } from 'react-native';

import { resolveChatAnchor, type ChatAnchor } from '@/domain/chat-anchor';
import type { ChatTimelineEntry } from '@/domain/chat-timeline';

const HIGHLIGHT_DURATION_MS = 2000;
const RETRY_DELAY_MS = 80;
const FALLBACK_VIEW_POSITION = 0.5;

type UseChatAnchorOptions = {
  loaded: boolean;
  listRef: React.RefObject<FlatList<ChatTimelineEntry> | null>;
  messageId?: string;
  onConsumed: () => void;
  timeline: ChatTimelineEntry[];
};

export type ChatScrollToIndexFailure = {
  averageItemLength: number;
  index: number;
};

export function useChatAnchor({ loaded, listRef, messageId, onConsumed, timeline }: UseChatAnchorOptions) {
  const [highlightedMessageId, setHighlightedMessageId] = useState<string | null>(null);
  const anchorRef = useRef<ChatAnchor | null>(null);
  const consumedRef = useRef<string | null>(null);
  const highlightStartRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const highlightTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const retryTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const clearTimers = useCallback(() => {
    if (highlightStartRef.current) clearTimeout(highlightStartRef.current);
    if (highlightTimerRef.current) clearTimeout(highlightTimerRef.current);
    if (retryTimerRef.current) clearTimeout(retryTimerRef.current);
    highlightStartRef.current = null;
    highlightTimerRef.current = null;
    retryTimerRef.current = null;
  }, []);

  useEffect(() => clearTimers, [clearTimers]);

  useEffect(() => {
    if (!messageId || !loaded || timeline.length === 0) return;
    if (consumedRef.current === messageId) return;
    consumedRef.current = messageId;
    anchorRef.current = null;
    const anchor = resolveChatAnchor(timeline, messageId);
    onConsumed();
    if (!anchor) return;
    anchorRef.current = anchor;
    listRef.current?.scrollToIndex({ animated: false, index: anchor.index, viewPosition: anchor.viewPosition });
    if (highlightStartRef.current) clearTimeout(highlightStartRef.current);
    if (highlightTimerRef.current) clearTimeout(highlightTimerRef.current);
    highlightStartRef.current = setTimeout(() => {
      highlightStartRef.current = null;
      setHighlightedMessageId(messageId);
    }, 0);
    highlightTimerRef.current = setTimeout(() => {
      highlightTimerRef.current = null;
      setHighlightedMessageId((current) => (current === messageId ? null : current));
    }, HIGHLIGHT_DURATION_MS);
  }, [listRef, loaded, messageId, onConsumed, timeline]);

  const handleScrollToIndexFailed = useCallback((info: ChatScrollToIndexFailure) => {
    const anchor = anchorRef.current;
    const matchesAnchor = anchor !== null && anchor.index === info.index;
    const fallbackOffset = matchesAnchor ? anchor.offset : 0;
    const offset = info.averageItemLength > 0 ? info.averageItemLength * info.index : fallbackOffset;
    const viewPosition = matchesAnchor ? anchor.viewPosition : FALLBACK_VIEW_POSITION;
    listRef.current?.scrollToOffset({ animated: false, offset });
    if (retryTimerRef.current) clearTimeout(retryTimerRef.current);
    retryTimerRef.current = setTimeout(() => {
      retryTimerRef.current = null;
      listRef.current?.scrollToIndex({ animated: true, index: info.index, viewPosition });
    }, RETRY_DELAY_MS);
  }, [listRef]);

  return { handleScrollToIndexFailed, highlightedMessageId };
}
