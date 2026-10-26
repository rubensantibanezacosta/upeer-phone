import type { ReactNode } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { CheckCheck, LogOut, Settings, Users, UserPlus, UsersRound } from '@tamagui/lucide-icons-2';
import { Text } from 'tamagui';

import { confirmSignOut } from '@/components/confirm-sign-out';
import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

type ChatListMenuProps = {
  onClose: () => void;
  onMarkAllRead: () => void;
  onNewConversation: () => void;
  onOpenContacts: () => void;
  onOpenSettings: () => void;
  onSignOut: () => void;
  onUnavailable: (label: string) => void;
};

export function ChatListMenu({ onClose, onMarkAllRead, onNewConversation, onOpenContacts, onOpenSettings, onSignOut, onUnavailable }: ChatListMenuProps) {
  const theme = useTheme();
  const scale = useTextScale();
  const run = (action: () => void) => () => {
    onClose();
    action();
  };

  const item = (icon: ReactNode, label: string, onPress: () => void, color: string = theme.text) => (
    <NativeRipplePressable accessibilityRole="button" onPress={onPress} style={styles.item}>
      {icon}
      <Text color={color} fontSize={scale(14)}>{label}</Text>
    </NativeRipplePressable>
  );

  return (
    <>
      <Pressable accessibilityLabel="Cerrar menú" onPress={onClose} style={styles.dismiss} />
      <View style={[styles.menu, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
        {item(<UserPlus color={theme.text} size={18} />, 'Nueva conversación', run(onNewConversation))}
        {item(<UsersRound color={theme.text} size={18} />, 'Nuevo grupo', run(() => onUnavailable('Nuevo grupo')))}
        {item(<CheckCheck color={theme.text} size={18} />, 'Marcar todo como leído', run(onMarkAllRead))}
        <View style={[styles.divider, { backgroundColor: theme.border }]} />
        {item(<Users color={theme.text} size={18} />, 'Contactos', run(onOpenContacts))}
        {item(<Settings color={theme.text} size={18} />, 'Configuración', run(onOpenSettings))}
        <View style={[styles.divider, { backgroundColor: theme.border }]} />
        {item(<LogOut color={theme.danger} size={18} />, 'Cerrar sesión', run(() => confirmSignOut(onSignOut)), theme.danger)}
      </View>
    </>
  );
}

const styles = StyleSheet.create({
  dismiss: { bottom: 0, left: 0, position: 'absolute', right: 0, top: 0, zIndex: 1 },
  menu: { borderRadius: 12, borderWidth: StyleSheet.hairlineWidth, elevation: 8, paddingVertical: 6, position: 'absolute', right: 20, top: 72, width: 220, zIndex: 2 },
  item: { alignItems: 'center', flexDirection: 'row', gap: 12, minHeight: 46, paddingHorizontal: 14 },
  divider: { height: StyleSheet.hairlineWidth, marginHorizontal: 10, marginVertical: 4 },
});
