import { StyleSheet, View } from 'react-native';
import { FileAudio, FileImage, FileText, FileVideo, Mic } from '@tamagui/lucide-icons-2';
import { SizableText } from 'tamagui';

import { attachmentLabel, formatFileSize, type AttachmentInfo } from '@/domain/attachment';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

function iconFor(info: AttachmentInfo, color: string) {
  const size = 20;
  switch (info.kind) {
    case 'image':
      return <FileImage color={color} size={size} strokeWidth={2} />;
    case 'video':
      return <FileVideo color={color} size={size} strokeWidth={2} />;
    case 'audio':
      return <FileAudio color={color} size={size} strokeWidth={2} />;
    case 'voice':
      return <Mic color={color} size={size} strokeWidth={2} />;
    default:
      return <FileText color={color} size={size} strokeWidth={2} />;
  }
}

export function ChatAttachment({ info, isMine }: { info: AttachmentInfo; isMine: boolean }) {
  const theme = useTheme();
  const scale = useTextScale();
  const textColor = isMine ? theme.bubbleMineText : theme.text;
  const metaColor = isMine ? theme.bubbleMineText : theme.placeholder;
  const size = formatFileSize(info.fileSize);

  return (
    <View accessibilityLabel={attachmentLabel(info)} style={styles.row}>
      <View style={styles.icon}>{iconFor(info, textColor)}</View>
      <View style={styles.texts}>
        <SizableText color={textColor} fontSize={scale(14)} fontWeight="600" numberOfLines={1}>
          {attachmentLabel(info)}
        </SizableText>
        <SizableText color={metaColor} fontSize={scale(11)} numberOfLines={1}>
          {size ? `${size} · Pendiente de descarga` : 'Pendiente de descarga'}
        </SizableText>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  icon: { justifyContent: 'center' },
  row: { alignItems: 'center', flexDirection: 'row', gap: 10, minWidth: 180 },
  texts: { flexShrink: 1, gap: 2 },
});
