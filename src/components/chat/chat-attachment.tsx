import { StyleSheet, View } from 'react-native';
import { Text } from 'tamagui';

import { ChatDocumentAttachment } from '@/components/chat/chat-document-attachment';
import { ChatMediaAttachment } from '@/components/chat/chat-media-attachment';
import { ChatVoiceNote } from '@/components/chat/chat-voice-note';
import { type AttachmentInfo, canOpenAttachment } from '@/domain/attachment';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';
import type { ProtocolMessage } from '../../../modules/upeer-transport/src/UPeerTransport.types';

export function ChatAttachment({ info, isMine, onOpen, onUnavailable, status, timestamp }: {
  info: AttachmentInfo;
  isMine: boolean;
  onOpen?: (info: AttachmentInfo) => void;
  onUnavailable?: (label: string) => void;
  status: ProtocolMessage['status'];
  timestamp: number;
}) {
  const theme = useTheme();
  const scale = useTextScale();
  const open = onOpen && canOpenAttachment(info) ? () => onOpen(info) : undefined;

  return (
    <View style={styles.wrapper}>
      {info.kind === 'voice' ? (
        <ChatVoiceNote info={info} isMine={isMine} onUnavailable={onUnavailable} status={status} timestamp={timestamp} />
      ) : info.kind === 'image' || info.kind === 'video' ? (
        <ChatMediaAttachment info={info} isMine={isMine} onOpen={open} onUnavailable={onUnavailable} />
      ) : (
        <ChatDocumentAttachment info={info} isMine={isMine} onOpen={open} onUnavailable={onUnavailable} />
      )}
      {info.caption ? (
        <Text color={isMine ? theme.bubbleMineText : theme.text} fontSize={scale(14)} style={styles.caption}>{info.caption}</Text>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  caption: { paddingHorizontal: 2, paddingTop: 6 },
  wrapper: { alignSelf: 'stretch' },
});
