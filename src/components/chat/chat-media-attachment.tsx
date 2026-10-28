import { Image, StyleSheet, View } from 'react-native';
import { FileImage, FileVideo } from '@tamagui/lucide-icons-2';
import { Text } from 'tamagui';

import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { attachmentDetail, attachmentLabel, attachmentUri, fileExtension, type AttachmentInfo } from '@/domain/attachment';
import { useAttachmentFile } from '@/hooks/use-attachment-file';
import { useTextScale } from '@/hooks/use-text-scale';
import { useVideoThumbnail } from '@/hooks/use-video-thumbnail';
import { useTheme } from '@/hooks/use-theme';

export function ChatMediaAttachment({ info, isMine, onOpen }: {
  info: AttachmentInfo;
  isMine: boolean;
  onOpen?: () => void;
}) {
  const theme = useTheme();
  const scale = useTextScale();
  const isVideo = info.kind === 'video';
  const Icon = isVideo ? FileVideo : FileImage;
  const extension = fileExtension(info.fileName);
  const localUri = attachmentUri(info);
  const downloaded = useAttachmentFile(localUri);
  const generated = useVideoThumbnail(isVideo && !info.thumbnail && downloaded ? localUri : null);
  const preview = info.thumbnail ?? (downloaded ? (isVideo ? generated : localUri) : null);

  return (
    <NativeRipplePressable
      accessibilityLabel={`${attachmentLabel(info)}${attachmentDetail(info) ? `, ${attachmentDetail(info)}` : ''}${downloaded ? '' : ', no está en este dispositivo'}`}
      accessibilityRole="imagebutton"
      onPress={() => onOpen?.()}
      style={[styles.frame, { backgroundColor: theme.backgroundSelected }]}
    >
      {preview ? (
        <Image resizeMode="cover" source={{ uri: preview }} style={styles.image} />
      ) : (
        <View style={styles.placeholder}>
          <Icon color={isMine ? theme.bubbleMineText : theme.placeholder} size={48} strokeWidth={1.5} />
          {extension ? <Text color={theme.placeholder} fontSize={scale(11)}>{extension}</Text> : null}
        </View>
      )}
      {!downloaded ? (
        <View style={styles.pendingBadge}>
          <Text color="#FFFFFF" fontSize={scale(10)} fontWeight="700">No está aquí</Text>
        </View>
      ) : null}
      {isVideo && preview ? (
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
  pendingBadge: { backgroundColor: 'rgba(0,0,0,0.55)', borderRadius: 8, left: 8, paddingHorizontal: 8, paddingVertical: 3, position: 'absolute', top: 8 },
  placeholder: { alignItems: 'center', gap: 8, justifyContent: 'center', padding: 24 },
  playBadge: { alignItems: 'center', backgroundColor: 'rgba(0,0,0,0.5)', borderRadius: 10, height: 50, justifyContent: 'center', width: 50 },
  playOverlay: { alignItems: 'center', bottom: 0, justifyContent: 'center', left: 0, position: 'absolute', right: 0, top: 0 },
});
