import { memo, useCallback, useMemo, useState } from 'react';
import { FlatList, StyleSheet, View, type LayoutChangeEvent } from 'react-native';
import { Car, Clock, Flag, Heart, Lightbulb, PawPrint, Search, Smile, Utensils, Volleyball } from '@tamagui/lucide-icons-2';
import { Input, SizableText, XStack, YStack } from 'tamagui';

import { EMOJI_GROUPS, emojisForGroup, searchEmojis } from '@/domain/emoji-catalog';
import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { useRecentEmojis } from '@/hooks/use-recent-emojis';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

const RECENT_TAB = 'recent';
const CELL = 38;
const VISIBLE_ROWS = 6;
const EMPTY_HEIGHT = 132;
const MIN_COLUMNS = 7;

const GROUP_ICONS: Record<string, typeof Smile> = {
  activities: Volleyball,
  flags: Flag,
  food: Utensils,
  objects: Lightbulb,
  people: PawPrint,
  recent: Clock,
  search: Search,
  smileys: Smile,
  symbols: Heart,
  travel: Car,
};

const TABS = [{ id: RECENT_TAB, label: 'Recientes' }, ...EMOJI_GROUPS];

type EmojiGridProps = {
  onPick: (emoji: string) => void;
};

export function EmojiGrid({ onPick }: EmojiGridProps) {
  const theme = useTheme();
  const scale = useTextScale();
  const { recent, remember } = useRecentEmojis();
  const [groupId, setGroupId] = useState(RECENT_TAB);
  const [search, setSearch] = useState('');
  const [width, setWidth] = useState(0);
  const query = search.trim();
  const columns = width > 0 ? Math.max(MIN_COLUMNS, Math.floor(width / CELL)) : 9;
  const cell = width > 0 ? Math.floor(width / columns) : CELL;

  const emojis = useMemo(() => {
    if (query) return searchEmojis(query);
    return groupId === RECENT_TAB ? recent : emojisForGroup(groupId);
  }, [groupId, query, recent]);

  const heading = query ? 'Resultados de búsqueda' : groupId === RECENT_TAB ? 'Usados recientemente' : EMOJI_GROUPS.find((item) => item.id === groupId)?.label ?? '';

  const pick = useCallback((emoji: string) => {
    remember(emoji);
    onPick(emoji);
  }, [onPick, remember]);

  const handleLayout = useCallback((event: LayoutChangeEvent) => {
    const next = Math.round(event.nativeEvent.layout.width);
    setWidth((current) => current === next ? current : next);
  }, []);

  const renderItem = useCallback(({ item }: { item: string }) => <EmojiCell emoji={item} onPick={pick} size={cell} />, [cell, pick]);
  const renderTab = useCallback(({ item }: { item: { id: string; label: string } }) => {
    const active = item.id === groupId;
    return <NativeRipplePressable
      accessibilityLabel={item.label}
      accessibilityRole="button"
      accessibilityState={{ selected: active }}
      onPress={() => setGroupId(item.id)}
      style={[styles.tab, { backgroundColor: active ? theme.backgroundSelected : 'transparent' }]}
    >
      <GroupIcon color={active ? theme.accent : theme.placeholder} groupId={item.id} size={19} />
    </NativeRipplePressable>;
  }, [groupId, theme.accent, theme.backgroundSelected, theme.placeholder]);

  const gridHeight = emojis.length === 0 ? EMPTY_HEIGHT : VISIBLE_ROWS * cell;

  return <YStack gap={8} onLayout={handleLayout}>
    <XStack alignItems="center" backgroundColor={theme.background} borderColor={theme.border} borderRadius={10} borderWidth={StyleSheet.hairlineWidth} gap={8} paddingHorizontal={10} paddingVertical={8}>
      <Search color={theme.placeholder} size={16} />
      <Input
        accessibilityLabel="Buscar emoji"
        backgroundColor="transparent"
        borderWidth={0}
        color={theme.text}
        flex={1}
        fontSize={scale(14)}
        onChangeText={setSearch}
        placeholder="Buscar emoji..."
        placeholderTextColor={theme.placeholder}
        style={styles.input}
        value={search}
      />
    </XStack>
    {query ? null : <FlatList
      contentContainerStyle={styles.tabs}
      data={TABS}
      horizontal
      keyExtractor={(item) => item.id}
      keyboardShouldPersistTaps="handled"
      renderItem={renderTab}
      showsHorizontalScrollIndicator={false}
      style={[styles.tabBar, { backgroundColor: theme.background }]}
    />}
    <View style={styles.heading}>
      <GroupIcon color={theme.placeholder} groupId={query ? 'search' : groupId} size={13} />
      <SizableText color={theme.placeholder} fontSize={scale(11)} fontWeight="600" numberOfLines={1}>{heading}</SizableText>
    </View>
    {emojis.length === 0
      ? <YStack alignItems="center" height={gridHeight} justifyContent="center" paddingHorizontal={12}>
        <SizableText color={theme.placeholder} fontSize={scale(13)} textAlign="center">
          {query ? 'No se encontraron emojis' : 'Todavía no has usado ningún emoji'}
        </SizableText>
      </YStack>
      : <FlatList
        data={emojis}
        getItemLayout={(_, index) => ({ index, length: cell, offset: Math.floor(index / columns) * cell })}
        initialNumToRender={columns * VISIBLE_ROWS}
        key={`${groupId}-${query}-${columns}`}
        keyExtractor={(emoji, index) => `${index}-${emoji}`}
        keyboardShouldPersistTaps="handled"
        maxToRenderPerBatch={columns * VISIBLE_ROWS}
        numColumns={columns}
        removeClippedSubviews
        renderItem={renderItem}
        showsVerticalScrollIndicator={false}
        style={{ height: gridHeight }}
        windowSize={3}
      />}
  </YStack>;
}

const EmojiCell = memo(function EmojiCell({ emoji, onPick, size }: { emoji: string; onPick: (emoji: string) => void; size: number }) {
  const theme = useTheme();
  const scale = useTextScale();
  return <NativeRipplePressable
    accessibilityLabel={`Emoji ${emoji}`}
    accessibilityRole="button"
    onPress={() => onPick(emoji)}
    style={[styles.cell, { height: size, width: size }]}
  >
    <SizableText color={theme.text} fontSize={scale(22)}>{emoji}</SizableText>
  </NativeRipplePressable>;
});

function GroupIcon({ color, groupId, size }: { color: string; groupId: string; size: number }) {
  const Icon = GROUP_ICONS[groupId] ?? Smile;
  return <Icon color={color} size={size} />;
}

const styles = StyleSheet.create({
  cell: { alignItems: 'center', justifyContent: 'center' },
  heading: { alignItems: 'center', flexDirection: 'row', gap: 5, paddingHorizontal: 4 },
  input: { paddingHorizontal: 0, paddingVertical: 0 },
  tab: { alignItems: 'center', borderRadius: 8, justifyContent: 'center', minWidth: 40, paddingVertical: 7 },
  tabBar: { borderRadius: 10, flexGrow: 0 },
  tabs: { gap: 2 },
});
