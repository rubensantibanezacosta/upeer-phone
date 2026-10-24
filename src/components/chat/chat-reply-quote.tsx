import { StyleSheet, View } from 'react-native';
import { Text } from 'tamagui';

import type { ReplyPreview } from '@/domain/chat-reply';
import { useTheme } from '@/hooks/use-theme';

type ChatReplyQuoteProps = {
  isMine: boolean;
  onPress?: () => void;
  reply: ReplyPreview;
};

export function ChatReplyQuote({ isMine, onPress, reply }: ChatReplyQuoteProps) {
  const theme = useTheme();
  const authorColor = isMine ? theme.bubbleMineText : theme.accent;
  const excerptColor = isMine ? theme.bubbleMineText : theme.textSecondary;

  const content = <>
    <Text color={authorColor} fontSize={11} fontWeight="700" numberOfLines={1}>{reply.author}</Text>
    <Text color={excerptColor} fontSize={12} numberOfLines={1} opacity={0.85}>{reply.excerpt}</Text>
  </>;

  const style = [styles.quote, { backgroundColor: theme.backgroundSelected, borderLeftColor: authorColor }];
  if (!onPress) return <View style={style}>{content}</View>;

  return (
    <View
      accessibilityHint="Lleva al mensaje citado"
      accessibilityLabel={`Respondiendo a ${reply.author}: ${reply.excerpt}`}
      accessibilityRole="button"
      onResponderRelease={onPress}
      onStartShouldSetResponder={() => true}
      style={style}
    >
      {content}
    </View>
  );
}

const styles = StyleSheet.create({
  quote: { alignSelf: 'stretch', borderLeftWidth: 3, borderRadius: 8, gap: 0, maxWidth: '100%', paddingHorizontal: 8, paddingVertical: 6 },
});
