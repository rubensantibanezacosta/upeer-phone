import { memo, useCallback, useEffect, useRef } from 'react';
import { FlatList, StyleSheet, Text, View } from 'react-native';
import { SizableText } from 'tamagui';

import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

export const EMOJI_MIN_CELL = 44;
export const EMOJI_ROWS = 3;

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
  pageId: string;
};

export const EmojiPage = memo(function EmojiPage({ cell, columns, emojis, height, onPick, pageId }: EmojiPageProps) {
  const theme = useTheme();
  const scale = useTextScale();
  const list = useRef<FlatList<string>>(null);
  const color = theme.text;
  const renderItem = useCallback(({ item }: { item: string }) => <EmojiCell cell={cell} color={color} emoji={item} onPick={onPick} />, [cell, color, onPick]);

  useEffect(() => {
    list.current?.scrollToOffset({ animated: false, offset: 0 });
  }, [pageId]);

  if (emojis.length === 0) {
    return <View style={[styles.empty, { height }]}>
      <SizableText color={theme.placeholder} fontSize={scale(13)} textAlign="center">Todavía no has usado ningún emoji</SizableText>
    </View>;
  }

  return <FlatList
    data={emojis}
    getItemLayout={(_, index) => ({ index, length: cell, offset: Math.floor(index / columns) * cell })}
    initialNumToRender={columns * (EMOJI_ROWS + 2)}
    key={`grid-${columns}`}
    keyExtractor={(_, index) => String(index)}
    keyboardShouldPersistTaps="handled"
    maxToRenderPerBatch={columns * (EMOJI_ROWS + 2)}
    numColumns={columns}
    ref={list}
    removeClippedSubviews
    renderItem={renderItem}
    showsVerticalScrollIndicator={false}
    style={{ height }}
    updateCellsBatchingPeriod={16}
    windowSize={3}
  />;
});

const EmojiCell = memo(function EmojiCell({ cell, color, emoji, onPick }: { cell: number; color: string; emoji: string; onPick: (emoji: string) => void }) {
  const size = Math.max(16, Math.round(cell * 0.58));
  return <NativeRipplePressable
    accessibilityLabel={`Emoji ${emoji}`}
    accessibilityRole="button"
    onPress={() => onPick(emoji)}
    style={[styles.cell, { height: cell, width: cell }]}
  >
    <Text allowFontScaling={false} style={[styles.glyph, { color, fontSize: size, lineHeight: cell }]}>{emoji}</Text>
  </NativeRipplePressable>;
});

const styles = StyleSheet.create({
  cell: { alignItems: 'center', justifyContent: 'center' },
  empty: { alignItems: 'center', justifyContent: 'center', paddingHorizontal: 12 },
  glyph: { includeFontPadding: false, textAlign: 'center', textAlignVertical: 'center' },
});
