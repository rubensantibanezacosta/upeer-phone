import { useMemo } from 'react';
import { StyleSheet, View } from 'react-native';
import { MapPin } from '@tamagui/lucide-icons-2';
import { Paragraph, SizableText, XStack, YStack } from 'tamagui';

import { describeLocationAddress, describePublication, type LocationTone } from '@/domain/location-status';
import { useLocationStatus } from '@/hooks/use-location-status';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

const TONE_COLORS: Record<LocationTone, string> = {
  ok: '#22c55e',
  warn: '#f59e0b',
  danger: '#ef4444',
  idle: '#6b7280',
};

export function LocationStatusCard() {
  const theme = useTheme();
  const scale = useTextScale();
  const { status, updatedAt } = useLocationStatus();

  const summary = useMemo(() => describePublication(status?.publication?.fanout, updatedAt), [status, updatedAt]);
  const address = describeLocationAddress(status?.lastAddress);
  const color = TONE_COLORS[summary.tone];

  return (
    <YStack backgroundColor={theme.backgroundElement} borderRadius={14} gap={10} padding={16}>
      <XStack alignItems="center" gap={10}>
        <View style={[styles.dot, { backgroundColor: `${color}22` }]}>
          <MapPin color={color} size={18} />
        </View>
        <YStack flex={1} gap={2}>
          <SizableText color={theme.text} fontSize={scale(15)} fontWeight="700">{summary.title}</SizableText>
          <Paragraph color={theme.placeholder} fontSize={scale(12)}>{summary.detail}</Paragraph>
        </YStack>
      </XStack>
      {address ? <Paragraph color={theme.placeholder} fontSize={scale(11)}>Dirección anunciada: {address}</Paragraph> : null}
      <YStack gap={2}>
        <Paragraph color={theme.placeholder} fontSize={scale(11)}>
          Publicar la ubicación no la comparte con nadie: solo indica a la red dónde encontrarte para que tus contactos admitidos puedan abrirte una conexión.
        </Paragraph>
      </YStack>
    </YStack>
  );
}

const styles = StyleSheet.create({
  dot: { alignItems: 'center', borderRadius: 10, height: 34, justifyContent: 'center', width: 34 },
});
