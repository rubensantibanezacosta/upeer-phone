import { StyleSheet, View } from 'react-native';
import { BadgeAlert, Info, Shield } from '@tamagui/lucide-icons-2';
import { SizableText, Text, XStack, YStack } from 'tamagui';

import { getTrustWarning, isLowReputation } from '@/domain/trust';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';
import type { OwnReputation } from '@/hooks/use-own-reputation';
import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { SettingsGroup, SettingsGroupLabel } from '@/components/settings-section-parts';

export function ReputationSection({ reputation, onInfo }: { reputation: OwnReputation | null; onInfo: () => void }) {
  const theme = useTheme();
  const scale = useTextScale();
  const vouchScore = reputation?.vouchScore;
  const hasReputation = vouchScore !== undefined;
  const connections = reputation?.connectionCount ?? 0;
  const warning = getTrustWarning(vouchScore);
  const low = isLowReputation(vouchScore);

  return (
    <YStack gap={10}>
      <SettingsGroupLabel
        action={<NativeRipplePressable accessibilityLabel="¿Qué es la reputación?" accessibilityRole="button" onPress={onInfo} style={styles.info}><Info color={theme.placeholder} size={16} /></NativeRipplePressable>}
        icon={<Shield color={theme.placeholder} size={15} />}
        title="Reputación"
      />
      <SettingsGroup>
        <YStack gap={10} padding={16}>
          {warning ? (
            <XStack alignItems="flex-start" gap={8}>
              <BadgeAlert color={theme.danger} size={15} strokeWidth={2.3} />
              <Text color={theme.danger} flex={1} fontSize={scale(12)} fontWeight="600">{warning}</Text>
            </XStack>
          ) : null}
          <XStack alignItems="baseline" gap={6}>
            <SizableText color={low ? theme.danger : theme.text} fontSize={scale(24)} fontWeight="700">{hasReputation ? Math.round(vouchScore) : '—'}</SizableText>
            <Text color={theme.placeholder} fontSize={scale(12)}>de 100</Text>
            <Text color={theme.placeholder} flex={1} fontSize={scale(11)} textAlign="right">{hasReputation ? `${connections} ${connections === 1 ? 'contacto directo' : 'contactos directos'}` : 'Calculando…'}</Text>
          </XStack>
          <View style={[styles.track, { backgroundColor: theme.backgroundSelected }]}>
            <View style={[styles.fill, { backgroundColor: low ? theme.danger : theme.accent, width: `${Math.min(Math.max(vouchScore ?? 0, 0), 100)}%` }]} />
          </View>
          <Text color={theme.placeholder} fontSize={scale(12)}>Se calcula con los avales que te dan tus contactos directos en los últimos 30 días.</Text>
        </YStack>
      </SettingsGroup>
    </YStack>
  );
}

const styles = StyleSheet.create({
  fill: { borderRadius: 4, height: 6 },
  info: { alignItems: 'center', borderRadius: 14, height: 28, justifyContent: 'center', overflow: 'hidden', width: 28 },
  track: { borderRadius: 4, height: 6, overflow: 'hidden' },
});
