import { StyleSheet, View } from 'react-native';
import { Text } from 'tamagui';

import type { ReactionSummary } from '@/domain/chat-reactions';
import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

type ChatReactionRowProps = {
  isMine: boolean;
  onToggle: (emoji: string) => void;
  reactions: ReactionSummary[];
};

export function ChatReactionRow({ isMine, onToggle, reactions }: ChatReactionRowProps) {
  const theme = useTheme();
  const scale = useTextScale();
  if (reactions.length === 0) return null;

  return (
    <View style={[styles.row, isMine ? styles.rowMine : styles.rowTheirs]}>
      {reactions.map((reaction) => (
        <NativeRipplePressable
          accessibilityLabel={`${reaction.emoji} ${reaction.count}`}
          accessibilityRole="button"
          accessibilityState={{ selected: reaction.mine }}
          key={reaction.emoji}
          onPress={() => onToggle(reaction.emoji)}
          style={[
            styles.chip,
            {
              backgroundColor: reaction.mine ? theme.accent : theme.backgroundElement,
              borderColor: theme.border,
            },
          ]}
        >
          <Text fontSize={scale(12)}>{reaction.emoji}</Text>
          {reaction.count > 1 ? (
            <Text color={reaction.mine ? theme.bubbleMineText : theme.textSecondary} fontSize={scale(11)} fontWeight="600">{reaction.count}</Text>
          ) : null}
        </NativeRipplePressable>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', flexWrap: 'wrap', gap: 4, marginTop: -2, width: '100%' },
  rowMine: { justifyContent: 'flex-end' },
  rowTheirs: { justifyContent: 'flex-start' },
  chip: {
    alignItems: 'center',
    borderRadius: 10,
    borderWidth: StyleSheet.hairlineWidth,
    flexDirection: 'row',
    gap: 3,
    paddingHorizontal: 6,
    paddingVertical: 1,
  },
});
