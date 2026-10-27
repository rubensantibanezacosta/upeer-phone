import { memo, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View, type NativeScrollEvent, type NativeSyntheticEvent, type ScrollViewInstance, type StyleProp, type TextStyle, type ViewStyle } from 'react-native';
import { SizableText } from 'tamagui';

import { emojiRowWindow } from '@/domain/emoji-pager';
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
  const [offsetY, setOffsetY] = useState(0);
  const scroller = useRef<ScrollViewInstance>(null);
  const rows = Math.ceil(emojis.length / columns);
  const visibleRows = Math.max(1, Math.ceil(height / cell));
  const cellStyle = useMemo<StyleProp<ViewStyle>>(() => [styles.cell, { height: cell, width: cell }], [cell]);
  const glyphStyle = useMemo<StyleProp<TextStyle>>(() => [styles.glyph, { color: theme.text, fontSize: Math.max(16, Math.round(cell * 0.58)), lineHeight: cell }], [cell, theme.text]);
  const ripple = useMemo(() => ({ borderless: false, color: theme.ripple }), [theme.ripple]);
  const contentStyle = useMemo<StyleProp<ViewStyle>>(() => ({ height: rows * cell }), [cell, rows]);

  useEffect(() => {
    scroller.current?.scrollTo({ animated: false, y: 0 });
    setOffsetY(0);
  }, [pageId]);

  const handleScroll = useCallback((event: NativeSyntheticEvent<NativeScrollEvent>) => {
    setOffsetY(event.nativeEvent.contentOffset.y);
  }, []);

  if (rows === 0) {
    return <View style={[styles.empty, { height }]}>
      <SizableText color={theme.placeholder} fontSize={scale(13)} textAlign="center">Todavía no has usado ningún emoji</SizableText>
    </View>;
  }

  const { first, last } = emojiRowWindow(offsetY, cell, rows, visibleRows);
  const visible: number[] = [];
  for (let row = first; row < last; row += 1) visible.push(row);

  return <ScrollView
    contentContainerStyle={contentStyle}
    keyboardShouldPersistTaps="handled"
    onScroll={handleScroll}
    ref={scroller}
    scrollEventThrottle={32}
    showsVerticalScrollIndicator={false}
    style={{ height }}
  >
    {visible.map((row) => <View key={row} style={[styles.row, { height: cell, top: row * cell }]}>
      {emojis.slice(row * columns, row * columns + columns).map((emoji) => <EmojiCell
        cellStyle={cellStyle}
        emoji={emoji}
        glyphStyle={glyphStyle}
        key={emoji}
        onPick={onPick}
        ripple={ripple}
      />)}
    </View>)}
  </ScrollView>;
});

type EmojiCellProps = {
  cellStyle: StyleProp<ViewStyle>;
  emoji: string;
  glyphStyle: StyleProp<TextStyle>;
  onPick: (emoji: string) => void;
  ripple: { borderless: boolean; color: string };
};

const EmojiCell = memo(function EmojiCell({ cellStyle, emoji, glyphStyle, onPick, ripple }: EmojiCellProps) {
  return <Pressable
    accessibilityLabel={`Emoji ${emoji}`}
    accessibilityRole="button"
    android_ripple={ripple}
    onPress={() => onPick(emoji)}
    style={cellStyle}
  >
    <Text allowFontScaling={false} style={glyphStyle}>{emoji}</Text>
  </Pressable>;
});

const styles = StyleSheet.create({
  cell: { alignItems: 'center', justifyContent: 'center' },
  empty: { alignItems: 'center', justifyContent: 'center', paddingHorizontal: 12 },
  glyph: { includeFontPadding: false, textAlign: 'center', textAlignVertical: 'center' },
  row: { flexDirection: 'row', left: 0, position: 'absolute', right: 0 },
});
