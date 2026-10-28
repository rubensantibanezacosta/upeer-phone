import { Modal, Pressable, StyleSheet, View } from 'react-native';
import { Image } from 'expo-image';
import { useVideoPlayer, VideoView } from 'expo-video';
import { SafeAreaView } from 'react-native-safe-area-context';
import { X } from '@tamagui/lucide-icons-2';
import { SizableText } from 'tamagui';

import { attachmentLabel, attachmentUri, type AttachmentInfo } from '@/domain/attachment';
import { useTextScale } from '@/hooks/use-text-scale';

function VideoPreview({ uri }: { uri: string }) {
  const player = useVideoPlayer(uri, (instance) => {
    instance.loop = false;
    instance.play();
  });
  return <VideoView contentFit="contain" nativeControls player={player} style={styles.media} />;
}

export function MediaViewer({ attachment, onClose }: {
  attachment: AttachmentInfo | null;
  onClose: () => void;
}) {
  const scale = useTextScale();
  const uri = attachment ? attachmentUri(attachment) : null;

  return (
    <Modal animationType="fade" onRequestClose={onClose} statusBarTranslucent transparent visible={attachment !== null}>
      <View style={styles.backdrop}>
        {attachment && uri ? (
          attachment.kind === 'video' ? <VideoPreview uri={uri} /> : <Image contentFit="contain" source={{ uri }} style={styles.media} />
        ) : null}
        <SafeAreaView edges={['top']} pointerEvents="box-none" style={styles.chrome}>
          <SizableText color="#FFFFFF" flex={1} fontSize={scale(14)} fontWeight="600" numberOfLines={1}>
            {attachment ? attachmentLabel(attachment) : ''}
          </SizableText>
          <Pressable accessibilityLabel="Cerrar" accessibilityRole="button" onPress={onClose} style={styles.close}>
            <X color="#FFFFFF" size={22} strokeWidth={2} />
          </Pressable>
        </SafeAreaView>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: { backgroundColor: '#000000', flex: 1, justifyContent: 'center' },
  chrome: { alignItems: 'center', flexDirection: 'row', gap: 12, left: 0, paddingHorizontal: 16, paddingVertical: 10, position: 'absolute', right: 0, top: 0 },
  close: { alignItems: 'center', backgroundColor: 'rgba(255,255,255,0.16)', borderRadius: 18, height: 36, justifyContent: 'center', width: 36 },
  media: { flex: 1, width: '100%' },
});
