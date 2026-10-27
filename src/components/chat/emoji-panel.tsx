import { memo } from 'react';
import { StyleSheet, View } from 'react-native';

import { EmojiGrid } from '@/components/chat/emoji-grid';
import { useTheme } from '@/hooks/use-theme';

type EmojiPanelProps = {
  onDelete: () => void;
  onPick: (emoji: string) => void;
  onSearchClosed: () => void;
};

export const EmojiPanel = memo(function EmojiPanel({ onDelete, onPick, onSearchClosed }: EmojiPanelProps) {
  const theme = useTheme();
  return (
    <View style={[styles.panel, { backgroundColor: theme.backgroundElement, borderTopColor: theme.border }]}>
      <EmojiGrid onDelete={onDelete} onPick={onPick} onSearchClosed={onSearchClosed} />
    </View>
  );
});

const styles = StyleSheet.create({
  panel: { borderTopWidth: StyleSheet.hairlineWidth, paddingBottom: 8, paddingHorizontal: 10, paddingTop: 8 },
});
