import { useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { Image } from 'expo-image';
import { FileImage, FileVideo } from '@tamagui/lucide-icons-2';
import { Text } from 'tamagui';

import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { attachmentDetail, attachmentLabel, attachmentUri, fileExtension, type AttachmentInfo } from '@/domain/attachment';
import { useAttachmentFile } from '@/hooks/use-attachment-file';
import { useTextScale } from '@/hooks/use-text-scale';
import { useVideoThumbnail } from '@/hooks/use-video-thumbnail';
import { useTheme } from '@/hooks/use-theme';

const PREVIEW_HEIGHT = 180;

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
  const [brokenPreview, setBrokenPreview] = useState<string | null>(null);
  const showPreview = Boolean(preview) && brokenPreview !== preview;

  return (
    <NativeRipplePressable
      accessibilityLabel={`${attachmentLabel(info)}${attachmentDetail(info) ? `, ${attachmentDetail(info)}` : ''}${downloaded ? '' : ', no está en este dispositivo'}`}
      accessibilityRole="imagebutton"
      onPress={() => onOpen?.()}
      style={[styles.frame, { backgroundColor: theme.backgroundSelected }]}
    >
      {showPreview ? (
        <Image
          contentFit="cover"
          onError={() => setBrokenPreview(preview as string)}
          source={{ uri: preview as string }}
          style={styles.image}
        />
      ) : (
        <View style={styles.placeholder}>
          <Icon color={isMine ? theme.bubbleMineText : theme.placeholder} size={40} strokeWidth={1.5} />
          {extension ? <Text color={theme.placeholder} fontSize={scale(11)}>{extension}</Text> : null}
        </View>
      )}
      {!downloaded ? (
        <View style={styles.pendingBadge}>
          <Text color="#FFFFFF" fontSize={scale(10)} fontWeight="700">No está aquí</Text>
        </View>
      ) : null}
      {isVideo && showPreview ? (
        <View style={styles.playOverlay}>
          <View style={styles.playBadge}>
            <FileVideo color="#FFFFFF" size={22} strokeWidth={2} />
          </View>
        </View>
      ) : null}
    </NativeRipplePressable>
  );
}

const styles = StyleSheet.create({
  frame: { borderRadius: 12, overflow: 'hidden', width: 260 },
  image: { height: PREVIEW_HEIGHT, width: '100%' },
  pendingBadge: { backgroundColor: 'rgba(0,0,0,0.55)', borderRadius: 8, left: 8, paddingHorizontal: 8, paddingVertical: 3, position: 'absolute', top: 8 },
  placeholder: { alignItems: 'center', gap: 8, justifyContent: 'center', minHeight: PREVIEW_HEIGHT, padding: 24 },
  playBadge: { alignItems: 'center', backgroundColor: 'rgba(0,0,0,0.5)', borderRadius: 10, height: 42, justifyContent: 'center', width: 42 },
  playOverlay: { alignItems: 'center', bottom: 0, justifyContent: 'center', left: 0, position: 'absolute', right: 0, top: 0 },
});
