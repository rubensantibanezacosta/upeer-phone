import { Modal, Pressable, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { ShieldCheck, UserMinus, X } from '@tamagui/lucide-icons-2';
import { SizableText, Text, XStack, YStack } from 'tamagui';

import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { ProfileAvatar } from '@/components/profile-avatar';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

type GroupMemberMenuProps = {
  memberId: string | null;
  name: string;
  canManage: boolean;
  onClose: () => void;
  onTransferAdmin: () => void;
  onRemove: () => void;
};

export function GroupMemberMenu({ memberId, name, canManage, onClose, onTransferAdmin, onRemove }: GroupMemberMenuProps) {
  const theme = useTheme();
  const scale = useTextScale();
  const insets = useSafeAreaInsets();
  if (!memberId) return null;

  return (
    <Modal animationType="fade" onRequestClose={onClose} presentationStyle="overFullScreen" statusBarTranslucent transparent visible>
      <View style={styles.overlay}>
        <Pressable accessibilityLabel="Cerrar menú del miembro" onPress={onClose} style={[StyleSheet.absoluteFill, { backgroundColor: theme.backdrop }]} />
        <View accessibilityViewIsModal style={[styles.menu, { backgroundColor: theme.backgroundElement, borderColor: theme.border, paddingBottom: Math.max(insets.bottom, 12) }]}>
          <XStack alignItems="center" gap={12} paddingHorizontal={16} paddingVertical={14}>
            <ProfileAvatar name={name} size={44} />
            <YStack flex={1} gap={2}>
              <SizableText color={theme.text} fontSize={scale(16)} fontWeight="700" numberOfLines={1}>{name}</SizableText>
              <Text color={theme.placeholder} fontSize={scale(11)} numberOfLines={1} style={styles.identifier}>{memberId}</Text>
            </YStack>
            <NativeRipplePressable accessibilityLabel="Cerrar menú" onPress={onClose} style={styles.closeButton}><X color={theme.placeholder} size={18} /></NativeRipplePressable>
          </XStack>
          <View style={[styles.divider, { backgroundColor: theme.border }]} />
          {canManage ? <>
            <NativeRipplePressable accessibilityLabel={`Hacer administrador a ${name}`} accessibilityRole="button" onPress={onTransferAdmin} style={styles.action}>
              <ShieldCheck color={theme.text} size={19} />
              <Text color={theme.text} fontSize={scale(14)} fontWeight="600">Hacer administrador</Text>
            </NativeRipplePressable>
            <NativeRipplePressable accessibilityLabel={`Expulsar a ${name}`} accessibilityRole="button" onPress={onRemove} style={styles.action}>
              <UserMinus color={theme.danger} size={19} />
              <Text color={theme.danger} fontSize={scale(14)} fontWeight="600">Expulsar del grupo</Text>
            </NativeRipplePressable>
          </> : <Text color={theme.placeholder} fontSize={scale(13)} paddingHorizontal={18} paddingVertical={14}>Solo el administrador puede gestionar a los miembros.</Text>}
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: { flex: 1, justifyContent: 'flex-end', paddingHorizontal: 12 },
  menu: { borderRadius: 18, borderWidth: StyleSheet.hairlineWidth, elevation: 14, overflow: 'hidden', shadowColor: '#000', shadowOffset: { height: 8, width: 0 }, shadowOpacity: 0.32, shadowRadius: 18 },
  action: { alignItems: 'center', flexDirection: 'row', gap: 14, minHeight: 52, paddingHorizontal: 18 },
  identifier: { fontFamily: 'monospace' },
  closeButton: { alignItems: 'center', borderRadius: 20, height: 40, justifyContent: 'center', width: 40 },
  divider: { height: StyleSheet.hairlineWidth, marginHorizontal: 16 },
});
