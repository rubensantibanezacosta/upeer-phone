import { Pressable, StyleSheet, View } from 'react-native';
import { useAudioPlayer, useAudioPlayerStatus } from 'expo-audio';
import { Download, Pause, Play } from '@tamagui/lucide-icons-2';
import { SizableText } from 'tamagui';

import { MessageStatus } from '@/components/chat/message-status';
import { formatChatTime } from '@/domain/chat-timeline';
import { attachmentUri, type AttachmentInfo } from '@/domain/attachment';
import { formatVoiceDuration } from '@/domain/voice-note';
import { useAttachmentFile } from '@/hooks/use-attachment-file';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';
import type { ProtocolMessage } from '../../../modules/upeer-transport/src/UPeerTransport.types';

const WAVE_HEIGHTS = [6, 9, 12, 8, 13, 10, 7, 12, 9, 6, 11, 14, 8, 12, 7, 10, 13, 9, 6, 12, 10, 7, 12, 8, 11, 14, 9, 6, 12, 10, 8, 12];

export function ChatVoiceNote({ info, isMine, onOpen, status, timestamp }: {
  info: AttachmentInfo;
  isMine: boolean;
  onOpen?: () => void;
  status?: ProtocolMessage['status'];
  timestamp: number;
}) {
  const theme = useTheme();
  const scale = useTextScale();
  const tint = isMine ? theme.bubbleMineText : theme.accent;
  const metaColor = isMine ? theme.bubbleMineText : theme.placeholder;
  const available = useAttachmentFile(attachmentUri(info));
  const downloaded = available;
  const uri = available ? attachmentUri(info) : null;
  const player = useAudioPlayer(uri);
  const playback = useAudioPlayerStatus(player);
  const playing = playback.playing;
  const position = playback.currentTime ?? 0;
  const length = playback.duration ?? 0;
  const finished = length > 0 && position >= length - 0.15;
  const playedRatio = length > 0 ? Math.min(1, position / length) : 0;
  const Icon = playing ? Pause : downloaded ? Play : Download;

  const toggle = () => {
    if (!uri || !downloaded) {
      onOpen?.();
      return;
    }
    if (playing) {
      player.pause();
      return;
    }
    if (finished) {
      void player.seekTo(0);
    }
    player.play();
  };

  return (
    <View accessibilityLabel={length > 0 ? `Nota de voz de ${formatVoiceDuration(length)}` : 'Nota de voz'} style={styles.row}>
      <Pressable
        accessibilityLabel={downloaded ? (playing ? 'Pausar nota de voz' : 'Reproducir nota de voz') : 'Nota de voz sin descargar'}
        accessibilityRole="button"
        onPress={toggle}
        style={({ pressed }) => [styles.play, pressed ? styles.playPressed : null]}
      >
        <Icon color={tint} fill={downloaded && !playing ? tint : undefined} size={20} strokeWidth={1.5} style={styles.playIcon} />
      </Pressable>
      <View style={styles.track}>
        <View style={styles.wave}>
          {WAVE_HEIGHTS.map((height, index) => {
            const played = downloaded && index / WAVE_HEIGHTS.length <= playedRatio;
            return <View key={index} style={[styles.bar, { backgroundColor: played ? tint : `${tint}66`, height }]} />;
          })}
        </View>
        <View style={styles.meta}>
          <SizableText color={metaColor} fontSize={scale(10)} numberOfLines={1} opacity={0.8}>
            {downloaded ? `${formatVoiceDuration(position)} / ${formatVoiceDuration(length)}` : 'Sin archivo'}
          </SizableText>
          <View style={styles.metaRight}>
            <SizableText color={metaColor} fontSize={scale(10)} opacity={0.8}>{formatChatTime(timestamp)}</SizableText>
            {isMine && status ? <MessageStatus color={metaColor} status={status} /> : null}
          </View>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  bar: { borderRadius: 1, width: 2 },
  meta: { alignItems: 'center', flexDirection: 'row', gap: 8, justifyContent: 'space-between', marginTop: 1 },
  metaRight: { alignItems: 'center', flexDirection: 'row', gap: 4 },
  play: { alignItems: 'center', alignSelf: 'flex-start', borderRadius: 16, height: 32, justifyContent: 'center', transform: [{ translateY: 3 }], width: 32 },
  playIcon: { marginLeft: 2 },
  playPressed: { opacity: 0.6 },
  row: { alignItems: 'flex-start', flexDirection: 'row', gap: 8, maxWidth: 320, paddingHorizontal: 2 },
  track: { flexShrink: 1, gap: 4 },
  wave: { alignItems: 'center', flexDirection: 'row', height: 14, justifyContent: 'space-between', marginTop: 8, overflow: 'hidden', transform: [{ translateY: 3 }], width: 170 },
});
