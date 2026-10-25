import { Image, StyleSheet, View } from 'react-native';
import { FileImage, FileVideo } from '@tamagui/lucide-icons-2';
import { Text } from 'tamagui';

import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { attachmentDetail, attachmentLabel, fileExtension, type AttachmentInfo } from '@/domain/attachment';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

export function ChatMediaAttachment({ info, isMine, onUnavailable }: {
  info: AttachmentInfo;
  isMine: boolean;
  onUnavailable?: (label: string) => void;
}) {
  const theme = useTheme();
  const scale = useTextScale();
  const isVideo = info.kind === 'video';
  const Icon = isVideo ? FileVideo : FileImage;
  const extension = fileExtension(info.fileName);

  return (
    <NativeRipplePressable
      accessibilityLabel={`${attachmentLabel(info)}${attachmentDetail(info) ? `, ${attachmentDetail(info)}` : ''}`}
      accessibilityRole="imagebutton"
      onPress={() => onUnavailable?.(isVideo ? 'Reproducir vídeo' : 'Abrir imagen')}
      style={[styles.frame, { backgroundColor: theme.backgroundSelected }]}
    >
      {info.thumbnail ? (
        <Image resizeMode="cover" source={{ uri: info.thumbnail }} style={styles.image} />
      ) : (
        <View style={styles.placeholder}>
          <Icon color={isMine ? theme.bubbleMineText : theme.placeholder} size={48} strokeWidth={1.5} />
          {extension ? <Text color={theme.placeholder} fontSize={scale(11)}>{extension}</Text> : null}
        </View>
      )}
      {isVideo && info.thumbnail ? (
        <View style={styles.playOverlay}>
          <View style={styles.playBadge}>
            <FileVideo color="#FFFFFF" size={26} strokeWidth={2} />
          </View>
        </View>
      ) : null}
    </NativeRipplePressable>
  );
}

const styles = StyleSheet.create({
  frame: { borderRadius: 12, overflow: 'hidden', width: 260 },
  image: { maxHeight: 300, width: '100%' },
  placeholder: { alignItems: 'center', gap: 8, justifyContent: 'center', padding: 24 },
  playBadge: { alignItems: 'center', backgroundColor: 'rgba(0,0,0,0.5)', borderRadius: 10, height: 50, justifyContent: 'center', width: 50 },
  playOverlay: { alignItems: 'center', bottom: 0, justifyContent: 'center', left: 0, position: 'absolute', right: 0, top: 0 },
});
