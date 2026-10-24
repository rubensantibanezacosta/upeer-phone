import { useState } from 'react';
import { Modal, Pressable, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { ArrowLeft, MoreVertical, Phone, Search, Video } from '@tamagui/lucide-icons-2';
import { SizableText, Text, XStack, YStack } from 'tamagui';

import { ContactTrustAvatar } from '@/components/contact-trust-avatar';
import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { CONTACT_STATUS_LABELS } from '@/domain/contact-labels';
import { getTrustLabel } from '@/domain/trust';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';
import type { UPeerContact } from '@/storage/contact-store';

type ChatHeaderProps = {
  alias: string;
  avatar?: string;
  upeerId: string;
  status: UPeerContact['status'];
  vouchScore?: number;
  isRequest?: boolean;
  typing?: boolean;
  presence?: string | null;
  onBack: () => void;
  onOpenInfo: () => void;
  onSearch: () => void;
  onUnavailable: (label: string) => void;
};

function HeaderAction({ label, onPress, children }: { label: string; onPress: () => void; children: React.ReactNode }) {
  return (
    <NativeRipplePressable accessibilityLabel={label} accessibilityRole="button" onPress={onPress} style={styles.action}>
      {children}
    </NativeRipplePressable>
  );
}

export function ChatHeader({ alias, avatar, upeerId, status, vouchScore, isRequest = false, typing = false, presence = null, onBack, onOpenInfo, onSearch, onUnavailable }: ChatHeaderProps) {
  const theme = useTheme();
  const scale = useTextScale();
  const insets = useSafeAreaInsets();
  const [menuOpen, setMenuOpen] = useState(false);
  const subtitle = typing ? 'Escribiendo…' : presence ?? CONTACT_STATUS_LABELS[status];
  const closeMenu = () => setMenuOpen(false);
  const openSearch = () => {
    closeMenu();
    onSearch();
  };

  return (
    <>
      <XStack alignItems="center" backgroundColor={theme.background} borderBottomColor={theme.border} borderBottomWidth={StyleSheet.hairlineWidth} gap={4} minHeight={72} paddingHorizontal={8} paddingVertical={8}>
        <HeaderAction label="Volver" onPress={onBack}><ArrowLeft color={theme.text} size={24} /></HeaderAction>
        <NativeRipplePressable
          accessibilityHint={`Identidad ${upeerId}`}
          accessibilityLabel={`Información de ${alias}. ${getTrustLabel(vouchScore)}`}
          accessibilityRole="button"
          onPress={onOpenInfo}
          style={styles.identity}
        >
          <ContactTrustAvatar avatar={avatar} name={alias} size={40} vouchScore={vouchScore} />
          <YStack flex={1} gap={2} minWidth={0}>
            <SizableText color={theme.text} fontSize={scale(16)} fontWeight="500" numberOfLines={1}>{alias}</SizableText>
            <Text color={typing ? theme.accent : theme.placeholder} fontSize={scale(12)} numberOfLines={1}>{subtitle}</Text>
          </YStack>
        </NativeRipplePressable>
        {isRequest ? null : <HeaderAction label="Videollamada" onPress={() => onUnavailable('Videollamada')}><Video color={theme.text} size={24} /></HeaderAction>}
        {isRequest ? null : <HeaderAction label="Llamada de voz" onPress={() => onUnavailable('Llamada de voz')}><Phone color={theme.text} size={22} /></HeaderAction>}
        <HeaderAction label="Opciones de la conversación" onPress={() => setMenuOpen(true)}><MoreVertical color={theme.text} size={24} /></HeaderAction>
      </XStack>
      <Modal animationType="fade" onRequestClose={closeMenu} presentationStyle="overFullScreen" statusBarTranslucent transparent visible={menuOpen}>
        <View style={styles.overlay}>
          <Pressable accessibilityLabel="Cerrar menú de opciones" onPress={closeMenu} style={StyleSheet.absoluteFill} />
          <View accessibilityViewIsModal style={[styles.menu, { backgroundColor: theme.backgroundElement, borderColor: theme.border, top: insets.top + 62 }]}>
            <NativeRipplePressable accessibilityRole="button" onPress={openSearch} style={styles.menuItem}>
              <Search color={theme.text} size={18} />
              <Text color={theme.text} fontSize={scale(14)}>Buscar en la conversación</Text>
            </NativeRipplePressable>
          </View>
        </View>
      </Modal>
    </>
  );
}

const styles = StyleSheet.create({
  action: { alignItems: 'center', borderRadius: 8, height: 40, justifyContent: 'center', overflow: 'hidden', width: 40 },
  identity: { alignItems: 'center', borderRadius: 12, flex: 1, flexDirection: 'row', gap: 12, minHeight: 48, minWidth: 0, paddingHorizontal: 6, paddingVertical: 4 },
  overlay: { flex: 1 },
  menu: { borderRadius: 12, borderWidth: StyleSheet.hairlineWidth, elevation: 8, paddingVertical: 6, position: 'absolute', right: 8, shadowColor: '#000', shadowOffset: { height: 6, width: 0 }, shadowOpacity: 0.28, shadowRadius: 14, width: 248 },
  menuItem: { alignItems: 'center', flexDirection: 'row', gap: 12, minHeight: 46, paddingHorizontal: 14 },
});
