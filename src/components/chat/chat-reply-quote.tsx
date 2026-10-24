import { StyleSheet, View } from 'react-native';
import { Text } from 'tamagui';

import type { ReplyPreview } from '@/domain/chat-reply';
import { useTheme } from '@/hooks/use-theme';

export function ChatReplyQuote({ isMine, reply }: { isMine: boolean; reply: ReplyPreview }) {
  const theme = useTheme();
  const barColor = isMine ? theme.bubbleMineText : theme.accent;

  return (
    <View style={[styles.quote, { backgroundColor: theme.backgroundSelected, borderLeftColor: barColor }]}>
      <Text color={barColor} fontSize={11} fontWeight="700" numberOfLines={1}>{reply.author}</Text>
      <Text color={theme.textSecondary} fontSize={11} numberOfLines={1}>{reply.excerpt}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  quote: { borderLeftWidth: 3, borderRadius: 6, gap: 1, maxWidth: '100%', paddingHorizontal: 8, paddingVertical: 4, width: '100%' },
});
