import { useMemo } from 'react';
import { StyleSheet, View } from 'react-native';
import { AlertTriangle, MapPin } from '@tamagui/lucide-icons-2';
import { Paragraph, Text, XStack, YStack } from 'tamagui';

import { SettingsGroup, SettingsGroupLabel, SettingsNote } from '@/components/settings-section-parts';
import { describeLocationAddress, describeLocationState } from '@/domain/location-status';
import { useLocationStatus } from '@/hooks/use-location-status';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

const WARNING_COLOR = '#f59e0b';

export function LocationStatusCard() {
  const theme = useTheme();
  const scale = useTextScale();
  const { status, updatedAt } = useLocationStatus();

  const publication = status?.publication?.fanout;
  const state = useMemo(() => describeLocationState(publication, updatedAt), [publication, updatedAt]);
  const address = describeLocationAddress(status?.lastAddress);

  return (
    <YStack gap={8}>
      <SettingsGroupLabel
        action={<View style={[styles.chip, { backgroundColor: state.published ? `${theme.success}22` : theme.backgroundSelected }]}><Text color={state.published ? theme.success : theme.placeholder} fontSize={scale(10)} fontWeight="700" letterSpacing={0.6}>{state.published ? 'PUBLICADA' : 'PENDIENTE'}</Text></View>}
        icon={<MapPin color={theme.placeholder} size={14} />}
        title="Tu ubicación en la red"
      >
        Dónde encontrarte dentro de la red de uPeer. No es la ubicación del teléfono: la app no pide GPS.
      </SettingsGroupLabel>

      <SettingsGroup>
        {state.rows.map((row) => <YStack gap={2} key={row.label} paddingHorizontal={14} paddingVertical={12}>
          <Text color={theme.text} fontSize={scale(13)} fontWeight="600">{row.label}</Text>
          <Paragraph color={theme.placeholder} fontSize={scale(12)}>{row.value}</Paragraph>
        </YStack>)}
        {state.errors.map((entry) => <XStack alignItems="flex-start" gap={10} key={entry.label} paddingHorizontal={14} paddingVertical={12}>
          <AlertTriangle color={WARNING_COLOR} size={16} style={styles.alert} />
          <YStack flex={1} gap={2}>
            <Text color={theme.text} fontSize={scale(13)} fontWeight="600">{entry.label}</Text>
            <Paragraph color={theme.placeholder} fontSize={scale(12)}>{entry.detail}</Paragraph>
          </YStack>
        </XStack>)}
        {address ? <YStack gap={2} paddingHorizontal={14} paddingVertical={12}>
          <Text color={theme.text} fontSize={scale(13)} fontWeight="600">Dirección anunciada</Text>
          <Paragraph color={theme.placeholder} fontSize={scale(12)}>{address}</Paragraph>
        </YStack> : null}
      </SettingsGroup>

      <SettingsNote>Este bloque es tu dirección de red, no tu posición: publicarla no revela dónde estás y sirve para que tus contactos admitidos puedan abrirte una conexión.</SettingsNote>
    </YStack>
  );
}

const styles = StyleSheet.create({
  alert: { marginTop: 2 },
  chip: { borderRadius: 6, paddingHorizontal: 7, paddingVertical: 2 },
});
