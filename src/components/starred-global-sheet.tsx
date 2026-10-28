import { useEffect, useMemo, useState } from 'react';
import { Modal, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Star, X } from '@tamagui/lucide-icons-2';
import { SizableText, Text, XStack } from 'tamagui';

import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { describeMessageContent } from '@/domain/attachment';
import { formatChatDateLabel, formatChatTime } from '@/domain/chat-timeline';
import { describeStarredTotal, groupStarredByChat, starredTotal, toStarredGlobalRows } from '@/domain/starred-global';
import type { ProtocolMessage } from '../../modules/upeer-transport/src/UPeerTransport.types';
import { getMessagesByIds } from '../../modules/upeer-transport/src/UPeerTransportModule';
import { useStarredMessages } from '@/hooks/use-starred-messages';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

type StarredGlobalSheetProps = {
  onClose: () => void;
  onOpen: (chatId: string, messageId: string) => void;
  titles: Record<string, string>;
  visible: boolean;
};

export function StarredGlobalSheet({ onClose, onOpen, titles, visible }: StarredGlobalSheetProps) {
  const theme = useTheme();
  const scale = useTextScale();
  const insets = useSafeAreaInsets();
  const { map } = useStarredMessages();
  const [loaded, setLoaded] = useState<ProtocolMessage[]>([]);

  const idKey = useMemo(() => Object.keys(map).join(','), [map]);

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

  const groups = useMemo(() => groupStarredByChat(toStarredGlobalRows(loaded, map), titles), [loaded, map, titles]);
  const total = starredTotal(groups);

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
          <Text color={theme.placeholder} fontSize={scale(11)} paddingBottom={8} paddingHorizontal={16}>{describeStarredTotal(total)}</Text>
          <View style={[styles.divider, { backgroundColor: theme.border }]} />
          {groups.length === 0 ? (
            <Text color={theme.placeholder} fontSize={scale(13)} padding={20} textAlign="center">Todavía no has destacado ningún mensaje.</Text>
          ) : (
            <ScrollView contentContainerStyle={styles.list} showsVerticalScrollIndicator={false}>
              {groups.map((group) => (
                <View key={group.chatId} style={styles.group}>
                  <XStack alignItems="center" gap={6} paddingHorizontal={16} paddingVertical={8}>
                    <SizableText color={theme.text} flex={1} fontSize={scale(12)} fontWeight="700" numberOfLines={1}>{group.name}</SizableText>
                    <Text color={theme.placeholder} fontSize={scale(10)}>{group.rows.length}</Text>
                  </XStack>
                  {group.rows.map((row) => (
                    <NativeRipplePressable accessibilityRole="button" key={row.id} onPress={() => { onClose(); onOpen(group.chatId, row.id); }} style={[styles.row, { borderColor: theme.border }]}>
                      <XStack alignItems="center" gap={8}>
                        <SizableText color={theme.placeholder} flex={1} fontSize={scale(10)} numberOfLines={1}>{row.isMine ? 'Tú' : 'Contacto'}</SizableText>
                        <Text color={theme.placeholder} fontSize={scale(10)}>{formatChatDateLabel(row.timestamp)} · {formatChatTime(row.timestamp)}</Text>
                      </XStack>
                      <Text color={theme.textSecondary} fontSize={scale(13)} numberOfLines={2}>{describeMessageContent(row.content)}</Text>
                    </NativeRipplePressable>
                  ))}
                </View>
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
  group: { paddingTop: 4 },
  row: { borderTopWidth: StyleSheet.hairlineWidth, gap: 4, marginHorizontal: 16, paddingVertical: 10 },
  closeButton: { alignItems: 'center', borderRadius: 20, height: 40, justifyContent: 'center', width: 40 },
});
