import { StyleSheet, View } from 'react-native';
import QRCode from 'react-native-qrcode-svg';
import { Ban, Clock3, UserCheck, UserPlus } from '@tamagui/lucide-icons-2';
import { Text } from 'tamagui';

import { BottomSheet } from '@/components/bottom-sheet';
import { NativeRippleButton } from '@/components/native-ripple-button';
import { ProfileAvatar } from '@/components/profile-avatar';
import type { ContactCard } from '@/domain/contact-card';
import { useTheme } from '@/hooks/use-theme';

export type ContactCardAction = {
  disabled: boolean;
  label: string;
  busy?: boolean;
  onPress: () => void;
};

function actionIcon(action: ContactCardAction, color: string) {
  if (action.busy) return <Clock3 color={color} size={16} />;
  if (action.label === 'Eres tú' || action.label === 'Ya guardado') return <UserCheck color={color} size={16} />;
  if (action.label === 'Contacto bloqueado') return <Ban color={color} size={16} />;
  if (action.label === 'Solicitud pendiente') return <Clock3 color={color} size={16} />;
  return <UserPlus color={color} size={16} />;
}

export function ContactCardSheet({ action, card, error, onClose, visible }: {
  action: ContactCardAction;
  card: ContactCard | null;
  error: string;
  onClose: () => void;
  visible: boolean;
}) {
  const theme = useTheme();
  if (!card) return null;
  const identity = card.address ? `${card.upeerId}@${card.address}` : card.upeerId;
  const name = card.name || card.upeerId.slice(0, 12);

  return <BottomSheet onClose={onClose} title="Tarjeta de contacto" visible={visible}>
    <View style={styles.identity}>
      <ProfileAvatar avatar={card.avatar} name={name} size={56} />
      <View style={styles.heading}>
        <Text color={theme.text} fontSize={16} fontWeight="700" numberOfLines={1}>{name}</Text>
        <Text color={theme.placeholder} fontSize={11}>Tarjeta de contacto</Text>
      </View>
    </View>

    <View style={[styles.detail, { backgroundColor: theme.background, borderColor: theme.border }]}>
      <Text color={theme.textSecondary} fontFamily="monospace" fontSize={11}>{identity}</Text>
    </View>

    <View style={[styles.qrCanvas, { borderColor: theme.border }]}>
      <QRCode ecl="M" size={180} value={identity} />
    </View>

    <NativeRippleButton
      backgroundColor="transparent"
      borderColor={theme.border}
      borderWidth={1}
      color={action.disabled ? theme.placeholder : theme.text}
      disabled={action.disabled}
      height={44}
      icon={actionIcon(action, action.disabled ? theme.placeholder : theme.text)}
      onPress={action.onPress}
      width="100%"
    >
      {action.label}
    </NativeRippleButton>

    {error ? <Text accessibilityRole="alert" color={theme.danger} fontSize={12}>{error}</Text> : null}
  </BottomSheet>;
}

const styles = StyleSheet.create({
  identity: { alignItems: 'center', flexDirection: 'row', gap: 14 },
  heading: { flex: 1, gap: 2 },
  detail: { alignItems: 'center', borderRadius: 10, borderWidth: StyleSheet.hairlineWidth, padding: 12 },
  qrCanvas: { alignItems: 'center', alignSelf: 'center', backgroundColor: '#FFFFFF', borderRadius: 12, borderWidth: StyleSheet.hairlineWidth, padding: 16 },
});
