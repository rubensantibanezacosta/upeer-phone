import { useEffect, useState, type ReactNode } from 'react';
import { Modal, Pressable, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Ban, Bell, BellOff, Download, Eraser, Info, LogOut, Search, Star, Trash2, X } from '@tamagui/lucide-icons-2';
import { SizableText, Text, XStack, YStack } from 'tamagui';

import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { flagsFor } from '@/domain/conversation-flags';
import { useChatOptions } from '@/hooks/use-chat-options';
import { useConversationFlags } from '@/hooks/use-conversation-flags';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';
import { getContacts } from '@/storage/contact-store';
import type { ProtocolMessage } from '../../../modules/upeer-transport/src/UPeerTransport.types';

type ChatOptionsMenuProps = {
  isGroup: boolean;
  messages: ProtocolMessage[];
  name: string;
  onChanged: () => Promise<void>;
  onCloseChat: () => void;
  onCloseMenu: () => void;
  onOpenInfo: () => void;
  onSearch: () => void;
  upeerId: string;
  visible: boolean;
};

type MenuActionProps = {
  color: string;
  icon: ReactNode;
  label: string;
  onPress: () => void;
};

function MenuAction({ color, icon, label, onPress }: MenuActionProps) {
  const scale = useTextScale();
  return (
    <NativeRipplePressable accessibilityRole="button" onPress={onPress} style={styles.action}>
      {icon}
      <Text color={color} fontSize={scale(14)} fontWeight="600">{label}</Text>
    </NativeRipplePressable>
  );
}

export function ChatOptionsMenu({ isGroup, messages, name, onChanged, onCloseChat, onCloseMenu, onOpenInfo, onSearch, upeerId, visible }: ChatOptionsMenuProps) {
  const theme = useTheme();
  const scale = useTextScale();
  const insets = useSafeAreaInsets();
  const flags = useConversationFlags();
  const muted = flagsFor(flags, upeerId).muted;
  const [isFavorite, setFavorite] = useState(false);
  const actions = useChatOptions({ isFavorite, isGroup, messages, muted, name, onChanged, onClose: onCloseChat, upeerId });

  useEffect(() => {
    if (!visible) return;
    getContacts()
      .then((contacts) => setFavorite(contacts.find((contact) => contact.upeerId === upeerId)?.isFavorite === true))
      .catch((cause: unknown) => console.warn('No se pudo leer el favorito de la conversación.', cause));
  }, [upeerId, visible]);

  const run = (action: () => void) => () => {
    onCloseMenu();
    action();
  };
  const toggleFavorite = run(() => {
    setFavorite((current) => !current);
    actions.toggleFavorite();
  });

  return (
    <Modal animationType="fade" onRequestClose={onCloseMenu} presentationStyle="overFullScreen" statusBarTranslucent transparent visible={visible}>
      <View style={styles.overlay}>
        <Pressable accessibilityLabel="Cerrar opciones de la conversación" onPress={onCloseMenu} style={[StyleSheet.absoluteFill, { backgroundColor: theme.backdrop }]} />
        <View accessibilityViewIsModal style={[styles.menu, { backgroundColor: theme.backgroundElement, borderColor: theme.border, paddingBottom: Math.max(insets.bottom, 12) }]}>
          <XStack alignItems="center" gap={12} paddingHorizontal={16} paddingVertical={14}>
            <YStack flex={1} gap={2}>
              <SizableText color={theme.text} fontSize={scale(16)} fontWeight="700" numberOfLines={1}>{name}</SizableText>
              <Text color={theme.placeholder} fontSize={scale(11)}>Opciones de la conversación</Text>
            </YStack>
            <NativeRipplePressable accessibilityLabel="Cerrar menú" onPress={onCloseMenu} style={styles.closeButton}><X color={theme.placeholder} size={18} /></NativeRipplePressable>
          </XStack>
          <View style={[styles.divider, { backgroundColor: theme.border }]} />
          <MenuAction color={theme.text} icon={<Info color={theme.text} size={19} />} label={isGroup ? 'Info. del grupo' : 'Info. del contacto'} onPress={run(onOpenInfo)} />
          <MenuAction color={theme.text} icon={<Search color={theme.text} size={19} />} label="Buscar" onPress={run(onSearch)} />
          <View style={[styles.divider, { backgroundColor: theme.border }]} />
          <MenuAction color={theme.text} icon={muted ? <Bell color={theme.text} size={19} /> : <BellOff color={theme.text} size={19} />} label={muted ? 'Reactivar notificaciones' : 'Silenciar notificaciones'} onPress={run(actions.toggleMute)} />
          <MenuAction color={theme.text} icon={<Star color={isFavorite ? theme.accent : theme.text} fill={isFavorite ? theme.accent : 'none'} size={19} />} label={isFavorite ? 'Quitar de Favoritos' : 'Añadir a Favoritos'} onPress={toggleFavorite} />
          <MenuAction color={theme.text} icon={<Download color={theme.text} size={19} />} label="Exportar chat" onPress={run(actions.exportChat)} />
          <View style={[styles.divider, { backgroundColor: theme.border }]} />
          <MenuAction color={theme.text} icon={<X color={theme.text} size={19} />} label="Cerrar chat" onPress={run(onCloseChat)} />
          {isGroup ? null : <MenuAction color={theme.text} icon={<Ban color={theme.text} size={19} />} label="Bloquear" onPress={run(actions.blockContact)} />}
          <MenuAction color={theme.text} icon={<Eraser color={theme.text} size={19} />} label="Vaciar chat" onPress={run(actions.clearChat)} />
          {isGroup ? (
            <MenuAction color={theme.danger} icon={<LogOut color={theme.danger} size={19} />} label="Salir del grupo" onPress={run(actions.leaveGroup)} />
          ) : (
            <MenuAction color={theme.danger} icon={<Trash2 color={theme.danger} size={19} />} label="Eliminar chat" onPress={run(actions.deleteChat)} />
          )}
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: { flex: 1, justifyContent: 'flex-end', paddingHorizontal: 12 },
  menu: { borderRadius: 18, borderWidth: StyleSheet.hairlineWidth, elevation: 14, overflow: 'hidden', shadowColor: '#000', shadowOffset: { height: 8, width: 0 }, shadowOpacity: 0.32, shadowRadius: 18 },
  action: { alignItems: 'center', flexDirection: 'row', gap: 14, minHeight: 52, paddingHorizontal: 18 },
  closeButton: { alignItems: 'center', borderRadius: 20, height: 40, justifyContent: 'center', width: 40 },
  divider: { height: StyleSheet.hairlineWidth, marginHorizontal: 16 },
});
