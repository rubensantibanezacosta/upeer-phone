import { useMemo, useState } from 'react';
import { ScrollView, StyleSheet, View } from 'react-native';
import { Car, Clock, Flag, Heart, Lightbulb, PawPrint, Search, Smile, Utensils, Volleyball } from '@tamagui/lucide-icons-2';
import { Input, SizableText, XStack, YStack } from 'tamagui';

import { EMOJI_GROUPS, emojisForGroup, searchEmojis } from '@/domain/emoji-catalog';
import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { useRecentEmojis } from '@/hooks/use-recent-emojis';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

const GROUP_ICONS: Record<string, typeof Smile> = {
  activities: Volleyball,
  flags: Flag,
  food: Utensils,
  objects: Lightbulb,
  people: PawPrint,
  smileys: Smile,
  symbols: Heart,
  travel: Car,
};

type EmojiGridProps = {
  onPick: (emoji: string) => void;
};

export function EmojiGrid({ onPick }: EmojiGridProps) {
  const theme = useTheme();
  const scale = useTextScale();
  const { recent, remember } = useRecentEmojis();
  const [groupId, setGroupId] = useState(EMOJI_GROUPS[0].id);
  const [search, setSearch] = useState('');
  const group = EMOJI_GROUPS.find((item) => item.id === groupId) ?? EMOJI_GROUPS[0];
  const query = search.trim();
  const results = useMemo(() => searchEmojis(query), [query]);

  const pick = (emoji: string) => {
    remember(emoji);
    onPick(emoji);
  };

  return <>
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
    {query ? null : <XStack backgroundColor={theme.background} borderRadius={10} overflow="hidden">
      {EMOJI_GROUPS.map((item) => {
        const active = item.id === groupId;
        return <NativeRipplePressable
          accessibilityLabel={item.label}
          accessibilityRole="button"
          accessibilityState={{ selected: active }}
          key={item.id}
          onPress={() => setGroupId(item.id)}
          style={[styles.tab, { backgroundColor: active ? theme.backgroundSelected : 'transparent' }]}
        >
          <GroupIcon color={active ? theme.accent : theme.placeholder} groupId={item.id} size={18} />
        </NativeRipplePressable>;
      })}
    </XStack>}
    <ScrollView contentContainerStyle={styles.scroll} keyboardShouldPersistTaps="handled" style={styles.list}>
      {query ? null : recent.length > 0 ? <YStack gap={4}>
        <XStack alignItems="center" gap={5} paddingHorizontal={4}>
          <Clock color={theme.placeholder} size={13} />
          <SizableText color={theme.placeholder} fontSize={scale(11)} fontWeight="600">Usados recientemente</SizableText>
        </XStack>
        <EmojiCells emojis={recent} onPick={pick} />
      </YStack> : null}
      <YStack gap={4}>
        <XStack alignItems="center" gap={5} paddingHorizontal={4}>
          {query ? <Search color={theme.placeholder} size={13} /> : <GroupIcon color={theme.placeholder} groupId={group.id} size={13} />}
          <SizableText color={theme.placeholder} fontSize={scale(11)} fontWeight="600">{query ? 'Resultados de búsqueda' : group.label}</SizableText>
        </XStack>
        {query && results.length === 0
          ? <SizableText color={theme.placeholder} fontSize={scale(13)} padding={12} textAlign="center">No se encontraron emojis</SizableText>
          : <EmojiCells emojis={query ? results : emojisForGroup(group.id)} onPick={pick} />}
      </YStack>
    </ScrollView>
  </>;
}

function GroupIcon({ color, groupId, size }: { color: string; groupId: string; size: number }) {
  const Icon = GROUP_ICONS[groupId] ?? Smile;
  return <Icon color={color} size={size} />;
}

function EmojiCells({ emojis, onPick }: { emojis: string[]; onPick: (emoji: string) => void }) {
  const theme = useTheme();
  const scale = useTextScale();
  return <View style={styles.cells}>
    {emojis.map((emoji, index) => <NativeRipplePressable
      accessibilityLabel={`Emoji ${emoji}`}
      accessibilityRole="button"
      key={`${emoji}-${index}`}
      onPress={() => onPick(emoji)}
      style={styles.cell}
    >
      <SizableText color={theme.text} fontSize={scale(22)}>{emoji}</SizableText>
    </NativeRipplePressable>)}
  </View>;
}

const styles = StyleSheet.create({
  cell: { alignItems: 'center', height: 36, justifyContent: 'center', width: 36 },
  cells: { flexDirection: 'row', flexWrap: 'wrap' },
  list: { maxHeight: 248 },
  scroll: { gap: 10, paddingVertical: 4 },
  input: { paddingHorizontal: 0, paddingVertical: 0 },
  tab: { alignItems: 'center', flex: 1, justifyContent: 'center', paddingVertical: 8 },
});
