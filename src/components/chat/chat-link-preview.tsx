import { useCallback } from 'react';
import { Image, Linking, StyleSheet, View } from 'react-native';
import { Globe, Images } from '@tamagui/lucide-icons-2';
import { Text } from 'tamagui';

import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import type { LinkPreview } from '@/domain/link-preview';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

export function ChatLinkPreview({ preview, tone }: { preview: LinkPreview; tone: 'mine' | 'theirs' }) {
  const theme = useTheme();
  const scale = useTextScale();
  const isMine = tone === 'mine';
  const titleColor = isMine ? theme.bubbleMineText : theme.text;
  const metaColor = isMine ? theme.bubbleMineText : theme.placeholder;

  const open = useCallback(() => {
    Linking.openURL(preview.url).catch((cause: unknown) => {
      console.warn('No se pudo abrir el enlace de la previsualización.', cause);
    });
  }, [preview.url]);

  return <NativeRipplePressable
    accessibilityLabel={`Abrir ${preview.domain ?? preview.url}`}
    accessibilityRole="link"
    onPress={open}
    style={[styles.frame, { backgroundColor: theme.backgroundElement }]}
  >
    <View style={[styles.thumb, { backgroundColor: theme.backgroundSelected }]}>
      {preview.imageBase64
        ? <Image resizeMode="cover" source={{ uri: preview.imageBase64 }} style={styles.image} />
        : <Images color={metaColor} opacity={0.5} size={26} strokeWidth={1.5} />}
    </View>
    <View style={styles.body}>
      <View style={styles.domain}>
        <Globe color={metaColor} opacity={0.8} size={11} strokeWidth={2} />
        <Text color={metaColor} fontSize={scale(10)} numberOfLines={1} style={styles.domainText}>{preview.domain ?? preview.url}</Text>
      </View>
      {preview.title ? <Text color={titleColor} fontSize={scale(13)} fontWeight="700" numberOfLines={2}>{preview.title}</Text> : null}
      {preview.description ? <Text color={metaColor} fontSize={scale(11)} numberOfLines={2} opacity={0.85}>{preview.description}</Text> : null}
    </View>
  </NativeRipplePressable>;
}

const styles = StyleSheet.create({
  body: { flex: 1, gap: 1, justifyContent: 'center', paddingHorizontal: 10, paddingVertical: 8 },
  domain: { alignItems: 'center', flexDirection: 'row', gap: 3 },
  domainText: { flexShrink: 1 },
  frame: { borderRadius: 10, flexDirection: 'row', minHeight: 64, overflow: 'hidden' },
  image: { height: '100%', width: '100%' },
  thumb: { alignItems: 'center', justifyContent: 'center', overflow: 'hidden', width: 72 },
});
