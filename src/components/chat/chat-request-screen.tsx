import { ScrollView, StyleSheet, View } from 'react-native';
import { Clock3, UserPlus } from '@tamagui/lucide-icons-2';
import { Paragraph, SizableText, Text, XStack, YStack } from 'tamagui';

import { ContactTrustAvatar } from '@/components/contact-trust-avatar';
import { NativeRippleButton } from '@/components/native-ripple-button';
import { getTrustDescriptor } from '@/domain/trust';
import { useTheme } from '@/hooks/use-theme';

type ChatRequestScreenProps = {
  alias: string;
  receivedAt: number;
  onAccept: () => void;
  onBlock: () => void;
};

function formatReceivedAt(timestamp: number): string {
  if (!Number.isFinite(timestamp) || timestamp <= 0) return '';
  const date = new Date(timestamp);
  const now = new Date();
  const time = date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  if (date.toDateString() === now.toDateString()) return `Hoy a las ${time}`;
  return `${date.toLocaleDateString([], { day: '2-digit', month: 'short', year: 'numeric' })} a las ${time}`;
}

export function ChatRequestScreen({ alias, receivedAt, onAccept, onBlock }: ChatRequestScreenProps) {
  const theme = useTheme();
  const trust = getTrustDescriptor(undefined);
  const received = formatReceivedAt(receivedAt);

  return (
    <ScrollView contentContainerStyle={styles.content}>
      <View style={[styles.avatarRing, { borderColor: theme.border }]}>
        <ContactTrustAvatar name={alias} size={80} />
      </View>
      <SizableText color={theme.text} fontSize={19} fontWeight="700" textAlign="center">Solicitud de conexión</SizableText>
      <Paragraph color={theme.textSecondary} fontSize={13} textAlign="center">
        <Text color={theme.text} fontSize={13} fontWeight="700">{alias}</Text> quiere establecer una conexión segura contigo. Al aceptar, la conversación pasará a tus contactos y se cifrará de extremo a extremo.
      </Paragraph>
      {received ? (
        <XStack alignItems="center" gap={5}>
          <Clock3 color={theme.placeholder} size={13} />
          <Text color={theme.placeholder} fontSize={11}>Solicitud recibida · {received}</Text>
        </XStack>
      ) : null}
      <XStack alignItems="flex-start" backgroundColor={theme.backgroundElement} borderRadius={12} gap={10} padding={12} width="100%">
        <UserPlus color={theme.placeholder} size={18} />
        <YStack flex={1} gap={2}>
          <Text color={theme.text} fontSize={13} fontWeight="700">{trust.label}</Text>
          <Text color={theme.placeholder} fontSize={11}>Ninguno de tus contactos ha avalado a esta identidad todavía. Puede ser normal en usuarios nuevos; confirma por otro canal que es quien dice ser.</Text>
        </YStack>
      </XStack>
      <XStack gap={10} width="100%">
        <NativeRippleButton backgroundColor={theme.backgroundElement} borderColor={theme.border} borderWidth={1} color={theme.text} flex={1} fontSize={15} height={46} onPress={onBlock}>Bloquear</NativeRippleButton>
        <NativeRippleButton backgroundColor={theme.accent} color={theme.onAccent} flex={1.6} fontSize={15} height={46} paddingHorizontal={10} onPress={onAccept}>Aceptar y conectar</NativeRippleButton>
      </XStack>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  content: { alignItems: 'center', flexGrow: 1, gap: 12, justifyContent: 'center', paddingHorizontal: 24, paddingVertical: 20 },
  avatarRing: { borderRadius: 48, borderWidth: 2, padding: 5 },
});
