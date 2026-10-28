import { StyleSheet } from 'react-native';
import { ArrowLeft, MoreVertical, Phone, Video } from '@tamagui/lucide-icons-2';
import { SizableText, Text, XStack, YStack } from 'tamagui';

import { ContactTrustAvatar } from '@/components/contact-trust-avatar';
import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import type { ConversationActivity } from '@/domain/chat-preview';
import { presenceText } from '@/domain/contact-labels';
import { getTrustAnnouncement } from '@/domain/trust';
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
  activity?: ConversationActivity | '';
  presence?: string | null;
  onBack: () => void;
  onOpenInfo: () => void;
  onOpenMenu: () => void;
  onUnavailable: (label: string) => void;
};

function HeaderAction({ label, onPress, children }: { label: string; onPress: () => void; children: React.ReactNode }) {
  return (
    <NativeRipplePressable accessibilityLabel={label} accessibilityRole="button" onPress={onPress} style={styles.action}>
      {children}
    </NativeRipplePressable>
  );
}

export function ChatHeader({ alias, avatar, upeerId, status, vouchScore, isRequest = false, activity = '', presence = null, onBack, onOpenInfo, onOpenMenu, onUnavailable }: ChatHeaderProps) {
  const theme = useTheme();
  const scale = useTextScale();
  const subtitle = activity === 'recording' ? 'Grabando audio…' : activity === 'typing' ? 'Escribiendo…' : presenceText(presence, status);

  return (
    <XStack alignItems="center" backgroundColor={theme.background} borderBottomColor={theme.border} borderBottomWidth={StyleSheet.hairlineWidth} gap={4} minHeight={72} paddingHorizontal={8} paddingVertical={8}>
      <HeaderAction label="Volver" onPress={onBack}><ArrowLeft color={theme.text} size={24} /></HeaderAction>
      <NativeRipplePressable
        accessibilityHint={`Identidad ${upeerId}`}
        accessibilityLabel={`Información de ${alias}. ${getTrustAnnouncement(vouchScore)}`}
        accessibilityRole="button"
        onPress={onOpenInfo}
        style={styles.identity}
      >
        <ContactTrustAvatar avatar={avatar} name={alias} size={40} vouchScore={vouchScore} />
        <YStack flex={1} gap={2} minWidth={0}>
          <SizableText color={theme.text} fontSize={scale(16)} fontWeight="500" numberOfLines={1}>{alias}</SizableText>
          <Text color={activity ? theme.accent : theme.placeholder} fontSize={scale(12)} numberOfLines={1}>{subtitle}</Text>
        </YStack>
      </NativeRipplePressable>
      {isRequest ? null : <HeaderAction label="Videollamada" onPress={() => onUnavailable('Videollamada')}><Video color={theme.text} size={24} /></HeaderAction>}
      {isRequest ? null : <HeaderAction label="Llamada de voz" onPress={() => onUnavailable('Llamada de voz')}><Phone color={theme.text} size={22} /></HeaderAction>}
      <HeaderAction label="Opciones de la conversación" onPress={onOpenMenu}><MoreVertical color={theme.text} size={24} /></HeaderAction>
    </XStack>
  );
}

const styles = StyleSheet.create({
  action: { alignItems: 'center', borderRadius: 8, height: 40, justifyContent: 'center', overflow: 'hidden', width: 40 },
  identity: { alignItems: 'center', borderRadius: 12, flex: 1, flexDirection: 'row', gap: 12, minHeight: 48, minWidth: 0, paddingHorizontal: 6, paddingVertical: 4 },
});
