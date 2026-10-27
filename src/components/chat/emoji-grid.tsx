import { useCallback, useMemo, useState } from 'react';
import { Animated, FlatList, PanResponder, StyleSheet, useWindowDimensions, type LayoutChangeEvent } from 'react-native';
import { Car, Clock, Flag, Heart, Lightbulb, PawPrint, Search, Smile, Utensils, Volleyball } from '@tamagui/lucide-icons-2';
import { Input, SizableText, XStack, YStack } from 'tamagui';

import { EMOJI_GROUPS, emojisForGroup, searchEmojis } from '@/domain/emoji-catalog';
import { nextPageIndex } from '@/domain/emoji-pager';
import { EMOJI_EMPTY_HEIGHT, EMOJI_MAX_ROWS, EMOJI_MIN_ROWS, EMOJI_ROWS_RATIO, EmojiPage, emojiMetrics } from '@/components/chat/emoji-page';
import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { useRecentEmojis } from '@/hooks/use-recent-emojis';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

const RECENT_TAB = 'recent';

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
const SWIPE_DISTANCE = 56;

function pageHeight(emojis: string[], gridHeight: number): number {
  return emojis.length === 0 ? EMOJI_EMPTY_HEIGHT : gridHeight;
}

type EmojiGridProps = {
  onPick: (emoji: string) => void;
};

export function EmojiGrid({ onPick }: EmojiGridProps) {
  const theme = useTheme();
  const scale = useTextScale();
  const window = useWindowDimensions();
  const { recent, remember } = useRecentEmojis();
  const [drag] = useState(() => new Animated.Value(0));
  const [search, setSearch] = useState('');
  const [width, setWidth] = useState(0);
  const [tabIndex, setTabIndex] = useState(1);
  const query = search.trim();
  const { cell, columns } = emojiMetrics(width);
  const rows = width > 0 ? Math.min(EMOJI_MAX_ROWS, Math.max(EMOJI_MIN_ROWS, Math.floor((window.height * EMOJI_ROWS_RATIO) / cell))) : EMOJI_MIN_ROWS;
  const gridHeight = rows * cell;

  const pages = useMemo(() => {
    if (query) return [{ id: 'search', label: 'Resultados de búsqueda', emojis: searchEmojis(query) }];
    return TABS.map((tab) => ({ id: tab.id, label: tab.id === RECENT_TAB ? 'Usados recientemente' : tab.label, emojis: tab.id === RECENT_TAB ? recent : emojisForGroup(tab.id) }));
  }, [query, recent]);

  const activeIndex = query ? 0 : Math.min(tabIndex, pages.length - 1);
  const active = pages[activeIndex];

  const pick = useCallback((emoji: string) => {
    remember(emoji);
    onPick(emoji);
  }, [onPick, remember]);

  const handleLayout = useCallback((event: LayoutChangeEvent) => {
    const next = Math.round(event.nativeEvent.layout.width);
    setWidth((current) => current === next ? current : next);
  }, []);

  const goToTab = useCallback((index: number) => {
    setTabIndex(nextPageIndex(index, 0, TABS.length));
  }, []);

  const swipe = useMemo(() => PanResponder.create({
    onMoveShouldSetPanResponder: (_, gesture) => !query && Math.abs(gesture.dx) > 14 && Math.abs(gesture.dx) > Math.abs(gesture.dy) * 1.4,
    onMoveShouldSetPanResponderCapture: (_, gesture) => !query && Math.abs(gesture.dx) > 14 && Math.abs(gesture.dx) > Math.abs(gesture.dy) * 1.4,
    onPanResponderMove: (_, gesture) => drag.setValue(gesture.dx),
    onPanResponderRelease: (_, gesture) => {
      if (gesture.dx < -SWIPE_DISTANCE) goToTab(activeIndex + 1);
      else if (gesture.dx > SWIPE_DISTANCE) goToTab(activeIndex - 1);
      Animated.spring(drag, { bounciness: 0, speed: 20, toValue: 0, useNativeDriver: false }).start();
    },
    onPanResponderTerminate: () => drag.setValue(0),
    onPanResponderTerminationRequest: () => true,
  }), [activeIndex, drag, goToTab, query]);

  const renderTab = useCallback(({ item, index }: { item: { id: string; label: string }; index: number }) => {
    const selected = index === activeIndex;
    return <NativeRipplePressable
      accessibilityLabel={item.label}
      accessibilityRole="button"
      accessibilityState={{ selected }}
      onPress={() => goToTab(index)}
      style={[styles.tab, { backgroundColor: selected ? theme.backgroundSelected : 'transparent' }]}
    >
      <GroupIcon color={selected ? theme.accent : theme.placeholder} groupId={item.id} size={19} />
    </NativeRipplePressable>;
  }, [activeIndex, goToTab, theme.accent, theme.backgroundSelected, theme.placeholder]);

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
    <XStack alignItems="center" gap={5} paddingHorizontal={4}>
      <GroupIcon color={theme.placeholder} groupId={active.id} size={13} />
      <SizableText color={theme.placeholder} flexShrink={1} fontSize={scale(11)} fontWeight="600" numberOfLines={1}>{active.label}</SizableText>
    </XStack>
    <Animated.View style={{ transform: [{ translateX: drag }] }} {...swipe.panHandlers}>
      <EmojiPage cell={cell} columns={columns} emojis={active.emojis} height={pageHeight(active.emojis, gridHeight)} onPick={pick} />
    </Animated.View>
  </YStack>;
}

function GroupIcon({ color, groupId, size }: { color: string; groupId: string; size: number }) {
  const Icon = GROUP_ICONS[groupId] ?? Smile;
  return <Icon color={color} size={size} />;
}

const styles = StyleSheet.create({
  input: { paddingHorizontal: 0, paddingVertical: 0 },
  tab: { alignItems: 'center', borderRadius: 8, justifyContent: 'center', minWidth: 40, paddingVertical: 7 },
  tabBar: { borderRadius: 10, flexGrow: 0 },
  tabs: { gap: 2 },
});
