import { StyleSheet, View } from 'react-native';
import { Text } from 'tamagui';

import { ChatDocumentAttachment } from '@/components/chat/chat-document-attachment';
import { ChatMediaAttachment } from '@/components/chat/chat-media-attachment';
import { ChatVoiceNote } from '@/components/chat/chat-voice-note';
import { type AttachmentInfo } from '@/domain/attachment';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';
import type { ProtocolMessage } from '../../../modules/upeer-transport/src/UPeerTransport.types';

export function ChatAttachment({ info, isMine, onOpen, status, timestamp }: {
  info: AttachmentInfo;
  isMine: boolean;
  onOpen?: (info: AttachmentInfo) => void;
  status: ProtocolMessage['status'];
  timestamp: number;
}) {
  const theme = useTheme();
  const scale = useTextScale();
  const open = onOpen ? () => onOpen(info) : undefined;

  return (
    <View style={styles.wrapper}>
      {info.kind === 'voice' ? (
        <ChatVoiceNote info={info} isMine={isMine} onOpen={open} status={status} timestamp={timestamp} />
      ) : info.kind === 'image' || info.kind === 'video' ? (
        <ChatMediaAttachment info={info} isMine={isMine} onOpen={open} />
      ) : (
        <ChatDocumentAttachment info={info} isMine={isMine} onOpen={open} />
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
