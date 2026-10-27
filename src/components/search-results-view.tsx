import { Pressable, StyleSheet, View } from 'react-native';
import { MessageCircle, Search as SearchIcon, Users } from '@tamagui/lucide-icons-2';
import { Paragraph, SizableText, Text, XStack, YStack } from 'tamagui';

import { LoadingState } from '@/components/loading-state';
import { NativeRippleButton } from '@/components/native-ripple-button';
import { highlightSegments } from '@/domain/search-highlight';
import type { SearchRow, SearchSection, SearchTarget } from '@/domain/global-search';
import { visibleSearchSections } from '@/domain/global-search';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

type SearchResultsViewProps = {
  query: string;
  sections: SearchSection[];
  loading: boolean;
  onOpenRow: (target: SearchTarget) => void;
  onClearQuery: () => void;
  onResetFilters: () => void;
};

function HighlightedText({ color, fontSize, numberOfLines, text, query }: { color: string; fontSize: number; numberOfLines?: number; text: string; query: string }) {
  const segments = highlightSegments(text, query);
  return <Text color={color} fontSize={fontSize} numberOfLines={numberOfLines}>
    {segments.map((segment, index) => <Text backgroundColor={segment.match ? 'rgba(120,180,255,0.28)' : undefined} fontSize={fontSize} fontWeight={segment.match ? '700' : '400'} key={`${index}-${segment.text}`}>{segment.text}</Text>)}
  </Text>;
}

function ResultRow({ query, row, onPress }: { query: string; row: SearchRow; onPress: () => void }) {
  const theme = useTheme();
  const scale = useTextScale();
  return <Pressable accessibilityRole="button" onPress={onPress} style={({ pressed }) => [styles.row, { backgroundColor: pressed ? theme.backgroundElement : 'transparent' }]}>
    <View style={[styles.rowIcon, { backgroundColor: theme.backgroundElement }]}>
      {row.target.kind === 'group' ? <Users color={theme.placeholder} size={16} /> : <MessageCircle color={theme.placeholder} size={16} />}
    </View>
    <YStack flex={1} gap={2}>
      <HighlightedText color={theme.text} fontSize={scale(15)} numberOfLines={1} query={query} text={row.title} />
      <HighlightedText color={theme.placeholder} fontSize={scale(12)} numberOfLines={2} query={query} text={row.subtitle} />
    </YStack>
  </Pressable>;
}

export function SearchResultsView({ query, sections, loading, onOpenRow, onClearQuery, onResetFilters }: SearchResultsViewProps) {
  const theme = useTheme();
  const scale = useTextScale();
  const visible = visibleSearchSections(sections);

  if (visible.length === 0) {
    return loading ? <LoadingState compact label="Buscando…" /> : <YStack alignItems="center" flex={1} gap={12} justifyContent="center" padding={24}>
      <View style={[styles.emptyAvatar, { backgroundColor: theme.backgroundElement }]}>
        <SearchIcon color={theme.placeholder} size={28} />
      </View>
      <SizableText color={theme.text} fontSize={scale(17)} fontWeight="600">Sin coincidencias</SizableText>
      <Paragraph color={theme.placeholder} textAlign="center">Nada coincide con «{query.trim()}» y el filtro activo.</Paragraph>
      <NativeRippleButton onPress={onClearQuery}>Limpiar búsqueda</NativeRippleButton>
      <NativeRippleButton backgroundColor={theme.backgroundElement} color={theme.text} onPress={onResetFilters}>Quitar filtro</NativeRippleButton>
    </YStack>;
  }

  return <YStack gap={4} marginTop={4}>
    {visible.map((section) => <YStack gap={2} key={section.id}>
      <XStack alignItems="center" justifyContent="space-between" paddingHorizontal={4} paddingVertical={6}>
        <Text color={theme.textSecondary} fontSize={scale(11)} fontWeight="700">{section.title.toUpperCase()}</Text>
        <Text color={theme.placeholder} fontSize={scale(11)} fontWeight="600">{section.total}</Text>
      </XStack>
      {section.rows.map((row) => <ResultRow key={row.key} onPress={() => onOpenRow(row.target)} query={query} row={row} />)}
    </YStack>)}
  </YStack>;
}

const styles = StyleSheet.create({
  emptyAvatar: { alignItems: 'center', borderRadius: 32, height: 64, justifyContent: 'center', width: 64 },
  row: { alignItems: 'center', borderRadius: 12, flexDirection: 'row', gap: 12, paddingHorizontal: 8, paddingVertical: 8 },
  rowIcon: { alignItems: 'center', borderRadius: 18, height: 36, justifyContent: 'center', width: 36 },
});
