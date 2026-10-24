import type { ReactElement } from 'react';
import { FlatList, StyleSheet, View, type ListRenderItemInfo, type NativeScrollEvent, type NativeSyntheticEvent } from 'react-native';

import { ChatEmptyState } from '@/components/chat/chat-empty-state';
import { ChatScrollButton } from '@/components/chat/chat-scroll-button';
import type { ChatTimelineEntry } from '@/domain/chat-timeline';

type ChatTimelineListProps = {
  data: ChatTimelineEntry[];
  listRef: React.RefObject<FlatList<ChatTimelineEntry> | null>;
  onScroll: (event: NativeSyntheticEvent<NativeScrollEvent>) => void;
  onScrollToLatest: () => void;
  renderItem: (info: ListRenderItemInfo<ChatTimelineEntry>) => ReactElement;
  showScrollButton: boolean;
};

const keyExtractor = (entry: ChatTimelineEntry) => entry.message.id;

export function ChatTimelineList({ data, listRef, onScroll, onScrollToLatest, renderItem, showScrollButton }: ChatTimelineListProps) {
  return (
    <View style={styles.timeline}>
      {data.length === 0 ? <ChatEmptyState /> : (
        <FlatList
          ref={listRef}
          contentContainerStyle={styles.listContent}
          data={data}
          initialNumToRender={24}
          inverted
          keyboardDismissMode="interactive"
          keyboardShouldPersistTaps="handled"
          keyExtractor={keyExtractor}
          maxToRenderPerBatch={16}
          onScroll={onScroll}
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
}

const styles = StyleSheet.create({
  timeline: { flex: 1, position: 'relative' },
  list: { flex: 1 },
  listContent: { flexGrow: 1, paddingHorizontal: 12, paddingVertical: 16 },
});
