import { memo, useCallback } from 'react';
import { FlatList, StyleSheet, View } from 'react-native';
import { SizableText } from 'tamagui';

import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

export const EMOJI_MIN_CELL = 44;
export const EMOJI_ROWS = 6;
export const EMOJI_MIN_ROWS = 4;
export const EMOJI_ROWS_RATIO = 0.3;
export const EMOJI_EMPTY_HEIGHT = 132;

export function emojiMetrics(width: number) {
  const columns = width > 0 ? Math.max(7, Math.floor(width / EMOJI_MIN_CELL)) : 7;
  const cell = width > 0 ? Math.floor(width / columns) : EMOJI_MIN_CELL;
  return { cell, columns };
}

type EmojiPageProps = {
  cell: number;
  columns: number;
  emojis: string[];
  height: number;
  onPick: (emoji: string) => void;
};

export const EmojiPage = memo(function EmojiPage({ cell, columns, emojis, height, onPick }: EmojiPageProps) {
  const theme = useTheme();
  const scale = useTextScale();
  const renderItem = useCallback(({ item }: { item: string }) => <EmojiCell cell={cell} emoji={item} onPick={onPick} />, [cell, onPick]);

  if (emojis.length === 0) {
    return <View style={[styles.empty, { height }]}>
      <SizableText color={theme.placeholder} fontSize={scale(13)} textAlign="center">Todavía no has usado ningún emoji</SizableText>
    </View>;
  }

  return <FlatList
    data={emojis}
    getItemLayout={(_, index) => ({ index, length: cell, offset: Math.floor(index / columns) * cell })}
    initialNumToRender={columns * EMOJI_ROWS}
    key={`${columns}-${Math.ceil(emojis.length / columns)}`}
    keyExtractor={(emoji, index) => `${index}-${emoji}`}
    keyboardShouldPersistTaps="handled"
    maxToRenderPerBatch={columns * EMOJI_ROWS}
    numColumns={columns}
    renderItem={renderItem}
    showsVerticalScrollIndicator={false}
    style={{ height }}
    windowSize={3}
  />;
});

const EmojiCell = memo(function EmojiCell({ cell, emoji, onPick }: { cell: number; emoji: string; onPick: (emoji: string) => void }) {
  const theme = useTheme();
  const scale = useTextScale();
  return <NativeRipplePressable
    accessibilityLabel={`Emoji ${emoji}`}
    accessibilityRole="button"
    onPress={() => onPick(emoji)}
    style={[styles.cell, { height: cell, width: cell }]}
  >
    <SizableText color={theme.text} fontSize={scale(24)} style={styles.glyph}>{emoji}</SizableText>
  </NativeRipplePressable>;
});

const styles = StyleSheet.create({
  cell: { alignItems: 'center', justifyContent: 'center' },
  empty: { alignItems: 'center', justifyContent: 'center', paddingHorizontal: 12 },
  glyph: { includeFontPadding: false, textAlign: 'center', textAlignVertical: 'center' },
});
