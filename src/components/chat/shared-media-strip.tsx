import { ScrollView, StyleSheet, View } from 'react-native';
import { Image } from 'expo-image';
import { FileText, Play } from '@tamagui/lucide-icons-2';

import type { SharedMediaItem } from '@/domain/shared-media';
import { useTheme } from '@/hooks/use-theme';

type SharedMediaStripProps = {
  items: SharedMediaItem[];
  onOpen: (item: SharedMediaItem) => void;
};

export function SharedMediaStrip({ items, onOpen }: SharedMediaStripProps) {
  const theme = useTheme();
  if (items.length === 0) return null;

  return <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.strip}>
    {items.map((item) => <View key={item.fileId} style={[styles.tile, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
      <Tile item={item} onOpen={onOpen} />
    </View>)}
  </ScrollView>;
}

function Tile({ item, onOpen }: { item: SharedMediaItem; onOpen: (item: SharedMediaItem) => void }) {
  const theme = useTheme();
  return <View accessibilityLabel={item.fileName} accessibilityRole="button" onTouchEnd={() => onOpen(item)} style={styles.tileFill}>
    {item.preview ? <>
      <Image contentFit="cover" source={{ uri: item.preview }} style={styles.image} />
      {item.isVideo ? <View style={styles.play}><Play color="white" size={16} /></View> : null}
    </> : <FileText color={theme.placeholder} size={20} />}
  </View>;
}

const styles = StyleSheet.create({
  strip: { gap: 6, paddingHorizontal: 16, paddingVertical: 12 },
  tile: { alignItems: 'center', borderRadius: 8, borderWidth: StyleSheet.hairlineWidth, height: 72, justifyContent: 'center', overflow: 'hidden', width: 72 },
  tileFill: { alignItems: 'center', height: '100%', justifyContent: 'center', width: '100%' },
  image: { height: '100%', width: '100%' },
  play: { alignItems: 'center', backgroundColor: 'rgba(0,0,0,0.4)', borderRadius: 12, height: 24, justifyContent: 'center', position: 'absolute', width: 24 },
});
