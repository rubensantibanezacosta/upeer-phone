import { useCallback, useEffect, useMemo, useState } from 'react';
import { Modal, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Star, X } from '@tamagui/lucide-icons-2';
import { SizableText, Text, XStack } from 'tamagui';

import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { describeMessageContent } from '@/domain/attachment';
import { formatChatDateLabel, formatChatTime } from '@/domain/chat-timeline';
import { collectStarred } from '@/domain/starred';
import type { ProtocolMessage } from '../../../modules/upeer-transport/src/UPeerTransport.types';
import { getMessagesByIds } from '../../../modules/upeer-transport/src/UPeerTransportModule';
import { useTextScale } from '@/hooks/use-text-scale';
import { useStarredMessages } from '@/hooks/use-starred-messages';
import { useTheme } from '@/hooks/use-theme';

type ChatStarredSheetProps = {
  messages: ProtocolMessage[];
  onClose: () => void;
  onOpenMessage: (messageID: string) => void;
  upeerId: string;
  visible: boolean;
};

export function ChatStarredSheet({ messages, onClose, onOpenMessage, upeerId, visible }: ChatStarredSheetProps) {
  const theme = useTheme();
  const scale = useTextScale();
  const insets = useSafeAreaInsets();
  const { map } = useStarredMessages();
  const [loaded, setLoaded] = useState<ProtocolMessage[]>([]);

  const ids = useMemo(() => Object.keys(map), [map]);
  const idKey = ids.join(',');

  useEffect(() => {
    if (!visible) return;
    let cancelled = false;
    getMessagesByIds(idKey ? idKey.split(',') : [])
      .then((page) => {
        if (!cancelled) setLoaded(page);
      })
      .catch((cause: unknown) => console.warn('No se pudieron cargar los mensajes destacados.', cause));
    return () => { cancelled = true; };
  }, [idKey, visible]);

  const starred = useMemo(() => {
    const known = new Map<string, ProtocolMessage>();
    for (const message of [...messages, ...loaded]) known.set(message.id, message);
    return collectStarred([...known.values()], map).filter((message) => message.upeerId === upeerId);
  }, [loaded, map, messages, upeerId]);

  const open = useCallback((message: ProtocolMessage) => {
    onClose();
    onOpenMessage(message.id);
  }, [onClose, onOpenMessage]);

  return (
    <Modal animationType="fade" onRequestClose={onClose} presentationStyle="overFullScreen" statusBarTranslucent transparent visible={visible}>
      <View style={styles.overlay}>
        <Pressable accessibilityLabel="Cerrar destacados" onPress={onClose} style={[StyleSheet.absoluteFill, { backgroundColor: theme.backdrop }]} />
        <View accessibilityViewIsModal style={[styles.sheet, { backgroundColor: theme.backgroundElement, borderColor: theme.border, paddingBottom: Math.max(insets.bottom, 12) }]}>
          <XStack alignItems="center" gap={10} paddingHorizontal={16} paddingVertical={14}>
            <Star color={theme.accent} fill={theme.accent} size={18} />
            <SizableText color={theme.text} flex={1} fontSize={scale(16)} fontWeight="700">Mensajes destacados</SizableText>
            <NativeRipplePressable accessibilityLabel="Cerrar destacados" onPress={onClose} style={styles.closeButton}><X color={theme.placeholder} size={18} /></NativeRipplePressable>
          </XStack>
          <View style={[styles.divider, { backgroundColor: theme.border }]} />
          {starred.length === 0 ? (
            <Text color={theme.placeholder} fontSize={scale(13)} padding={20} textAlign="center">Todavía no has destacado ningún mensaje en esta conversación.</Text>
          ) : (
            <ScrollView contentContainerStyle={styles.list} showsVerticalScrollIndicator={false}>
              {starred.map((message) => (
                <NativeRipplePressable accessibilityRole="button" key={message.id} onPress={() => open(message)} style={[styles.row, { borderColor: theme.border }]}>
                  <XStack alignItems="center" gap={8}>
                    <SizableText color={theme.text} flex={1} fontSize={scale(11)} fontWeight="700" numberOfLines={1}>
                      {message.isMine ? 'Tú' : 'Contacto'}
                    </SizableText>
                    <Text color={theme.placeholder} fontSize={scale(10)}>
                      {formatChatDateLabel(message.timestamp)} · {formatChatTime(message.timestamp)}
                    </Text>
                  </XStack>
                  <Text color={theme.textSecondary} fontSize={scale(13)} numberOfLines={2}>{describeMessageContent(message.content)}</Text>
                </NativeRipplePressable>
              ))}
            </ScrollView>
          )}
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: { flex: 1, justifyContent: 'flex-end', paddingHorizontal: 12 },
  sheet: { borderRadius: 18, borderWidth: StyleSheet.hairlineWidth, elevation: 14, maxHeight: '80%', overflow: 'hidden', shadowColor: '#000', shadowOffset: { height: 8, width: 0 }, shadowOpacity: 0.32, shadowRadius: 18 },
  divider: { height: StyleSheet.hairlineWidth, marginHorizontal: 16 },
  list: { paddingBottom: 8 },
  row: { borderTopWidth: StyleSheet.hairlineWidth, gap: 4, paddingHorizontal: 16, paddingVertical: 10 },
  closeButton: { alignItems: 'center', borderRadius: 20, height: 40, justifyContent: 'center', width: 40 },
});
