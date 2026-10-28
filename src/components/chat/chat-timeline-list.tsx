import type { ReactElement } from 'react';
import { memo, useCallback } from 'react';
import { FlatList, StyleSheet, View, type ListRenderItemInfo, type NativeScrollEvent, type NativeSyntheticEvent } from 'react-native';

import { ChatActivityBubble } from '@/components/chat/chat-activity-bubble';
import { ChatEmptyState } from '@/components/chat/chat-empty-state';
import { ChatRetentionNotice } from '@/components/chat/chat-retention-notice';
import { ChatScrollButton } from '@/components/chat/chat-scroll-button';
import { ChatStyleBackground } from '@/components/chat/chat-style-background';
import type { ConversationActivity } from '@/domain/chat-preview';
import type { ChatTimelineEntry } from '@/domain/chat-timeline';
import type { ChatScrollToIndexFailure } from '@/hooks/use-chat-anchor';
import { LoadingState } from '@/components/loading-state';

type ChatTimelineListProps = {
  activity?: ConversationActivity | '';
  data: ChatTimelineEntry[];
  listRef: React.RefObject<FlatList<ChatTimelineEntry> | null>;
  loading?: boolean;
  notice?: string | null;
  onScroll: (event: NativeSyntheticEvent<NativeScrollEvent>) => void;
  onScrollToIndexFailed?: (info: ChatScrollToIndexFailure) => void;
  onScrollToLatest: () => void;
  renderItem: (info: ListRenderItemInfo<ChatTimelineEntry>) => ReactElement;
  showScrollButton: boolean;
  styleId?: string | null;
};

const keyExtractor = (entry: ChatTimelineEntry) => entry.message.id;

export const ChatTimelineList = memo(function ChatTimelineList({ activity = '', data, listRef, loading = false, notice = null, onScroll, onScrollToIndexFailed, onScrollToLatest, renderItem, showScrollButton, styleId = null }: ChatTimelineListProps) {
  const fallbackScrollToIndexFailed = useCallback((info: ChatScrollToIndexFailure) => {
    listRef.current?.scrollToOffset({ animated: false, offset: info.averageItemLength * info.index });
    setTimeout(() => {
      listRef.current?.scrollToIndex({ animated: true, index: info.index, viewPosition: 0.5 });
    }, 80);
  }, [listRef]);

  return (
    <View style={styles.timeline}>
      <ChatStyleBackground styleId={styleId} />
      {data.length === 0 ? (loading ? <LoadingState label="Cargando mensajes…" /> : <ChatEmptyState />) : (
        <FlatList
          ref={listRef}
          contentContainerStyle={styles.listContent}
          data={data}
          initialNumToRender={24}
          inverted
          keyboardDismissMode="interactive"
          keyboardShouldPersistTaps="handled"
          keyExtractor={keyExtractor}
          ListHeaderComponent={<>{activity ? <ChatActivityBubble activity={activity} /> : null}<ChatRetentionNotice text={notice} /></>}
          maxToRenderPerBatch={16}
          onScroll={onScroll}
          onScrollToIndexFailed={onScrollToIndexFailed ?? fallbackScrollToIndexFailed}
          renderItem={renderItem}
          scrollEventThrottle={16}
          showsVerticalScrollIndicator={false}
          style={styles.list}
          updateCellsBatchingPeriod={40}
          windowSize={7}
        />
      )}
      {showScrollButton && data.length > 0 ? <ChatScrollButton onPress={onScrollToLatest} /> : null}
    </View>
  );
});

const styles = StyleSheet.create({
  timeline: { flex: 1, position: 'relative' },
  list: { flex: 1 },
  listContent: { flexGrow: 1, paddingHorizontal: 12, paddingVertical: 16 },
});
