import { useCallback, useState } from 'react';
import { StyleSheet, useWindowDimensions, View, type LayoutChangeEvent } from 'react-native';

import { EmojiGrid } from '@/components/chat/emoji-grid';
import { EMOJI_MIN_ROWS, EMOJI_ROWS_RATIO, emojiMetrics } from '@/components/chat/emoji-page';
import { useTheme } from '@/hooks/use-theme';

type EmojiPanelProps = {
  onPick: (emoji: string) => void;
};

export function EmojiPanel({ onPick }: EmojiPanelProps) {
  const theme = useTheme();
  const window = useWindowDimensions();
  const [width, setWidth] = useState(0);

  const handleLayout = useCallback((event: LayoutChangeEvent) => {
    const next = Math.round(event.nativeEvent.layout.width);
    setWidth((current) => (current === next ? current : next));
  }, []);

  const { cell } = emojiMetrics(width);
  const rows = Math.max(EMOJI_MIN_ROWS, Math.min(6, Math.floor((window.height * EMOJI_ROWS_RATIO) / cell)));

  return (
    <View onLayout={handleLayout} style={[styles.panel, { backgroundColor: theme.backgroundElement, borderTopColor: theme.border }]}>
      <EmojiGrid onPick={onPick} rows={rows} />
    </View>
  );
}

const styles = StyleSheet.create({
  panel: { borderTopWidth: StyleSheet.hairlineWidth, paddingBottom: 6, paddingHorizontal: 10, paddingTop: 8 },
});
