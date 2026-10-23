import { StyleSheet, View } from 'react-native';
import { Phone, Search, Video } from '@tamagui/lucide-icons-2';
import { SizableText, Text, XStack, YStack } from 'tamagui';

import { ContactTrustAvatar } from '@/components/contact-trust-avatar';
import { InteractiveRow } from '@/components/contact-info/interactive-row';
import { CONTACT_STATUS_LABELS } from '@/domain/contact-labels';
import { getTrustLabel } from '@/domain/trust';
import { useTheme } from '@/hooks/use-theme';
import type { UPeerContact } from '@/storage/contact-store';

type ContactInfoHeroProps = {
  alias: string;
  upeerId: string;
  status: UPeerContact['status'];
  vouchScore?: number;
  onCall: () => void;
  onVideo: () => void;
  onSearch: () => void;
};

export function ContactInfoHero({ alias, upeerId, status, vouchScore, onCall, onVideo, onSearch }: ContactInfoHeroProps) {
  const theme = useTheme();
  const statusColor = status === 'connected' ? theme.success : status === 'blocked' ? theme.danger : theme.placeholder;

  return (
    <YStack alignItems="center" gap={5} paddingHorizontal={20} paddingTop={4}>
      <ContactTrustAvatar name={alias} size={96} vouchScore={vouchScore} />
      <SizableText color={theme.text} fontSize={20} fontWeight="700" numberOfLines={2} textAlign="center">{alias}</SizableText>
      <Text color={theme.placeholder} ellipsizeMode="middle" fontFamily="monospace" fontSize={12} numberOfLines={1}>{upeerId}</Text>
      <XStack alignItems="center" gap={5}>
        <View style={[styles.dot, { backgroundColor: statusColor }]} />
        <Text color={statusColor} fontSize={12}>{CONTACT_STATUS_LABELS[status]}</Text>
      </XStack>
      <Text color={theme.placeholder} fontSize={12}>{getTrustLabel(vouchScore)}</Text>
      <XStack gap={18} paddingTop={12}>
        <InteractiveRow accessibilityLabel="Llamar" disabled icon={Phone} label="Llamar" onPress={onCall} variant="action" />
        <InteractiveRow accessibilityLabel="Videollamada" disabled icon={Video} label="Vídeo" onPress={onVideo} variant="action" />
        <InteractiveRow accessibilityLabel="Buscar en la conversación" disabled icon={Search} label="Buscar" onPress={onSearch} variant="action" />
      </XStack>
    </YStack>
  );
}

const styles = StyleSheet.create({ dot: { borderRadius: 4, height: 7, width: 7 } });
