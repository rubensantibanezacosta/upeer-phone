import { StyleSheet } from 'react-native';
import { MessageSquarePlus } from '@tamagui/lucide-icons-2';
import { SizableText, Text, XStack, YStack } from 'tamagui';

import { ContactTrustAvatar } from '@/components/contact-trust-avatar';
import { NativeRippleButton } from '@/components/native-ripple-button';
import type { ConversationCandidate } from '@/domain/incoming-requests';
import { requestLabel } from '@/domain/incoming-requests';
import { useTheme } from '@/hooks/use-theme';

type MessageRequestListProps = {
  requests: ConversationCandidate[];
  onAccept: (request: ConversationCandidate) => void;
  onBlock: (request: ConversationCandidate) => void;
};

export function MessageRequestList({ requests, onAccept, onBlock }: MessageRequestListProps) {
  const theme = useTheme();
  if (requests.length === 0) return null;

  return (
    <YStack gap={8}>
      <XStack alignItems="center" gap={8}>
        <MessageSquarePlus color={theme.placeholder} size={15} />
        <Text color={theme.placeholder} fontSize={12} fontWeight="600">Solicitudes de mensaje</Text>
        <Text color={theme.accent} fontSize={12} fontWeight="700">{requests.length}</Text>
      </XStack>
      {requests.map((request) => (
        <YStack key={request.upeerId} gap={10} style={[styles.card, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
          <XStack alignItems="center" gap={12}>
            <ContactTrustAvatar name={requestLabel(request)} size={40} />
            <YStack flex={1} gap={2} minWidth={0}>
              <SizableText color={theme.text} fontSize={15} fontWeight="600" numberOfLines={1}>{requestLabel(request)}</SizableText>
              <Text color={theme.placeholder} fontSize={12} numberOfLines={1}>{request.preview || 'Quiere escribirte'}</Text>
            </YStack>
          </XStack>
          <XStack gap={8}>
            <NativeRippleButton backgroundColor={theme.accent} color={theme.onAccent} flex={1} height={38} onPress={() => onAccept(request)}>Aceptar</NativeRippleButton>
            <NativeRippleButton backgroundColor={theme.dangerSoft} borderColor={theme.dangerBorder} borderWidth={1} color={theme.danger} flex={1} height={38} onPress={() => onBlock(request)}>Bloquear</NativeRippleButton>
          </XStack>
        </YStack>
      ))}
    </YStack>
  );
}

const styles = StyleSheet.create({
  card: { borderRadius: 14, borderWidth: StyleSheet.hairlineWidth, gap: 10, padding: 12 },
});
