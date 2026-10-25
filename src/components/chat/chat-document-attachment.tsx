import { StyleSheet, View } from 'react-native';
import { Download, FileText } from '@tamagui/lucide-icons-2';
import { Text } from 'tamagui';

import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { attachmentDetail, attachmentLabel, type AttachmentInfo } from '@/domain/attachment';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

export function ChatDocumentAttachment({ info, isMine, onUnavailable }: {
  info: AttachmentInfo;
  isMine: boolean;
  onUnavailable?: (label: string) => void;
}) {
  const theme = useTheme();
  const scale = useTextScale();
  const titleColor = isMine ? theme.bubbleMineText : theme.text;
  const detailColor = isMine ? theme.bubbleMineText : theme.placeholder;
  const circleColor = isMine ? `${theme.bubbleMineText}2E` : theme.accentSoft;
  const iconColor = isMine ? theme.bubbleMineText : theme.onAccentSoft;

  return (
    <NativeRipplePressable
      accessibilityHint="Descarga el archivo"
      accessibilityLabel={attachmentLabel(info)}
      accessibilityRole="button"
      onPress={() => onUnavailable?.('Descargar archivo')}
      style={[styles.card, { backgroundColor: isMine ? `${theme.bubbleMineText}14` : theme.backgroundSelected }]}
    >
      <View style={[styles.circle, { backgroundColor: circleColor }]}>
        <FileText color={iconColor} size={22} strokeWidth={2} />
      </View>
      <View style={styles.texts}>
        <Text color={titleColor} fontSize={scale(14)} fontWeight="600" numberOfLines={1}>{info.fileName}</Text>
        <Text color={detailColor} fontSize={scale(11)} numberOfLines={1}>{attachmentDetail(info)}</Text>
      </View>
      {isMine ? null : <Download color={iconColor} size={20} strokeWidth={2} />}
    </NativeRipplePressable>
  );
}

const styles = StyleSheet.create({
  card: {
    alignItems: 'center',
    borderRadius: 10,
    flexDirection: 'row',
    gap: 12,
    maxWidth: 320,
    minWidth: 240,
    overflow: 'hidden',
    padding: 12,
  },
  circle: { alignItems: 'center', borderRadius: 22, height: 44, justifyContent: 'center', width: 44 },
  texts: { flexShrink: 1, gap: 3 },
});
