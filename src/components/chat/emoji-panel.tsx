import { StyleSheet, View } from 'react-native';

import { EmojiGrid } from '@/components/chat/emoji-grid';
import { useTheme } from '@/hooks/use-theme';

type EmojiPanelProps = {
  onPick: (emoji: string) => void;
};

export function EmojiPanel({ onPick }: EmojiPanelProps) {
  const theme = useTheme();
  return (
    <View style={[styles.panel, { backgroundColor: theme.backgroundElement, borderTopColor: theme.border }]}>
      <EmojiGrid onPick={onPick} />
    </View>
  );
}

const styles = StyleSheet.create({
  panel: { borderTopWidth: StyleSheet.hairlineWidth, paddingBottom: 8, paddingHorizontal: 10, paddingTop: 8 },
});
