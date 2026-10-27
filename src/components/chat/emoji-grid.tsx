import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ScrollView, StyleSheet, View, type LayoutChangeEvent, type NativeScrollEvent, type NativeSyntheticEvent, type ScrollViewInstance } from 'react-native';import { Car, Clock, Flag, Heart, Lightbulb, PawPrint, Search, Smile, Utensils, Volleyball } from '@tamagui/lucide-icons-2';
import { Input, SizableText, XStack, YStack } from 'tamagui';

import { EMOJI_GROUPS, searchEmojis } from '@/domain/emoji-catalog';
import { pageIndexFromOffset, pageOffset } from '@/domain/emoji-pager';
import { EMOJI_ROWS, EmojiPage, emojiMetrics } from '@/components/chat/emoji-page';
import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { getRecentEmojis } from '@/storage/recent-emojis-store';
import { useRecentEmojis } from '@/hooks/use-recent-emojis';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

const RECENT_TAB = 'recent';
const PRELOAD_STEP_MS = 60;

function initialTabIndex(): number {
  return getRecentEmojis().length > 0 ? 0 : 1;
}

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
const GROUP_EMOJIS = new Map(EMOJI_GROUPS.map((group) => [group.id, group.emojis]));

type EmojiPageModel = {
  id: string;
  label: string;
  emojis: string[];
};

type EmojiGridProps = {
  onPick: (emoji: string) => void;
};

export function EmojiGrid({ onPick }: EmojiGridProps) {
  const theme = useTheme();
  const scale = useTextScale();
  const { recent, remember } = useRecentEmojis();
  const pager = useRef<ScrollViewInstance>(null);
  const [search, setSearch] = useState('');
  const [width, setWidth] = useState(0);
  const [tabIndex, setTabIndex] = useState(initialTabIndex);
  const [warm, setWarm] = useState<readonly number[]>(() => [initialTabIndex()]);
  const activeTab = useRef(tabIndex);
  const query = search.trim();
  const { cell, columns } = emojiMetrics(width);
  const gridHeight = EMOJI_ROWS * cell;

  const pages = useMemo<EmojiPageModel[]>(() => {
    if (query) return [{ id: 'search', label: 'Resultados de búsqueda', emojis: searchEmojis(query) }];
    return TABS.map((tab) => ({
      id: tab.id,
      label: tab.id === RECENT_TAB ? 'Usados recientemente' : tab.label,
      emojis: tab.id === RECENT_TAB ? recent : GROUP_EMOJIS.get(tab.id) ?? [],
    }));
  }, [query, recent]);

  const activeIndex = query ? 0 : Math.min(tabIndex, pages.length - 1);
  const active = pages[activeIndex];

  const warmUp = useCallback((index: number) => {
    setWarm((current) => (current.includes(index) ? current : [...current, index]));
  }, []);

  const isMounted = (index: number) => query || warm.includes(index) || Math.abs(index - activeIndex) <= 1;

  const alignToTab = useCallback(() => {
    if (width === 0) return;
    pager.current?.scrollTo({ animated: false, x: pageOffset(activeTab.current, width, pages.length), y: 0 });
  }, [pages.length, width]);

  useEffect(() => {
    activeTab.current = tabIndex;
    alignToTab();
  }, [alignToTab, tabIndex]);

  useEffect(() => {
    if (query) return;
    const timers = pages.map((_, index) => setTimeout(() => warmUp(index), PRELOAD_STEP_MS * (index + 1)));
    return () => {
      for (const timer of timers) clearTimeout(timer);
    };
  }, [pages, query, warmUp]);

  const pick = useCallback((emoji: string) => {
    remember(emoji);
    onPick(emoji);
  }, [onPick, remember]);

  const handleLayout = useCallback((event: LayoutChangeEvent) => {
    const next = Math.round(event.nativeEvent.layout.width);
    setWidth((current) => (current === next ? current : next));
  }, []);

  const goToTab = useCallback((index: number) => {
    setTabIndex(index);
    warmUp(index);
    pager.current?.scrollTo({ animated: true, x: pageOffset(index, width, pages.length), y: 0 });
  }, [pages.length, warmUp, width]);

  const handleSettled = useCallback((event: NativeSyntheticEvent<NativeScrollEvent>) => {
    if (query) return;
    const next = pageIndexFromOffset(event.nativeEvent.contentOffset.x, width, pages.length);
    setTabIndex(next);
    warmUp(next);
  }, [pages.length, query, warmUp, width]);

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
    {query ? null : <ScrollView contentContainerStyle={styles.tabs} horizontal showsHorizontalScrollIndicator={false} style={[styles.tabBar, { backgroundColor: theme.background }]}>
      {TABS.map((tab, index) => <TabButton active={index === activeIndex} index={index} key={tab.id} onPress={goToTab} tab={tab} />)}
    </ScrollView>}
    <XStack alignItems="center" gap={5} paddingHorizontal={4}>
      <GroupIcon color={theme.placeholder} groupId={active.id} size={13} />
      <SizableText color={theme.placeholder} flexShrink={1} fontSize={scale(11)} fontWeight="600" numberOfLines={1}>{active.label}</SizableText>
    </XStack>
    <ScrollView
      contentContainerStyle={{ width: (width || 1) * pages.length }}
      horizontal
      keyboardShouldPersistTaps="handled"
      onContentSizeChange={alignToTab}
      onMomentumScrollEnd={handleSettled}
      pagingEnabled
      ref={pager}
      scrollEnabled={!query && width > 0}
      showsHorizontalScrollIndicator={false}
      style={[styles.pager, { height: gridHeight }]}
    >
      {pages.map((page, index) => <View key={page.id} style={{ height: gridHeight, width: width || 1 }}>
        {isMounted(index) ? <EmojiPage
          cell={cell}
          columns={columns}
          emojis={page.emojis}
          height={gridHeight}
          onPick={pick}
          pageId={page.id}
        /> : null}
      </View>)}
    </ScrollView>
  </YStack>;
}

function TabButton({ active, index, onPress, tab }: { active: boolean; index: number; onPress: (index: number) => void; tab: { id: string; label: string } }) {
  const theme = useTheme();
  return <NativeRipplePressable
    accessibilityLabel={tab.label}
    accessibilityRole="button"
    accessibilityState={{ selected: active }}
    onPress={() => onPress(index)}
    style={[styles.tab, { backgroundColor: active ? theme.backgroundSelected : 'transparent' }]}
  >
    <GroupIcon color={active ? theme.accent : theme.placeholder} groupId={tab.id} size={19} />
  </NativeRipplePressable>;
}

function GroupIcon({ color, groupId, size }: { color: string; groupId: string; size: number }) {
  const Icon = GROUP_ICONS[groupId] ?? Smile;
  return <Icon color={color} size={size} />;
}

const styles = StyleSheet.create({
  input: { paddingHorizontal: 0, paddingVertical: 0 },
  pager: { flexGrow: 0 },
  tab: { alignItems: 'center', borderRadius: 8, justifyContent: 'center', minWidth: 40, paddingVertical: 7 },
  tabBar: { borderRadius: 10, flexGrow: 0 },
  tabs: { gap: 2 },
});
