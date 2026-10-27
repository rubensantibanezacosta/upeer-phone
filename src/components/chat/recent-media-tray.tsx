import { ScrollView, StyleSheet, View } from 'react-native';
import { Image } from 'expo-image';
import { Play } from '@tamagui/lucide-icons-2';
import { SizableText } from 'tamagui';

import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { formatDuration, type RecentMediaItem } from '@/services/recent-media';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

const TILE = 62;

type RecentMediaTrayProps = {
  items: RecentMediaItem[];
  loading: boolean;
  onDark?: boolean;
  onPick: (item: RecentMediaItem) => void;
};

export function RecentMediaTray({ items, loading, onDark = false, onPick }: RecentMediaTrayProps) {
  const theme = useTheme();
  const scale = useTextScale();
  const label = onDark ? '#fff' : theme.placeholder;

  return <View style={styles.wrap}>
    <SizableText color={label} fontSize={scale(11)} fontWeight="700" numberOfLines={1} paddingHorizontal={2}>
      {loading ? 'Buscando fotos recientes…' : items.length > 0 ? 'Recientes' : 'Sin fotos recientes'}
    </SizableText>
    <ScrollView contentContainerStyle={styles.row} horizontal showsHorizontalScrollIndicator={false}>
      {items.map((item) => <NativeRipplePressable
        accessibilityLabel={`Enviar ${item.title}`}
        accessibilityRole="button"
        key={item.id}
        onPress={() => onPick(item)}
        style={[styles.tile, { backgroundColor: onDark ? 'rgba(255,255,255,0.12)' : theme.background }]}
      >
        <Image contentFit="cover" source={{ uri: item.uri }} style={styles.image} />
        {item.duration ? <View style={styles.badge}>
          <Play color="#fff" fill="#fff" size={9} />
          <SizableText color="#fff" fontSize={9} fontWeight="700">{formatDuration(item.duration)}</SizableText>
        </View> : null}
      </NativeRipplePressable>)}
    </ScrollView>
  </View>;
}

const styles = StyleSheet.create({
  badge: { alignItems: 'center', backgroundColor: 'rgba(0,0,0,0.55)', borderRadius: 6, bottom: 4, flexDirection: 'row', gap: 3, paddingHorizontal: 4, paddingVertical: 1, position: 'absolute', right: 4 },
  image: { height: '100%', width: '100%' },
  row: { gap: 6, paddingVertical: 2 },
  tile: { borderRadius: 10, height: TILE, overflow: 'hidden', width: TILE },
  wrap: { gap: 6 },
});
