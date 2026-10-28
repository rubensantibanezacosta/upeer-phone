import { Alert, StyleSheet, View } from 'react-native';
import { ShieldCheck, Trash2 } from '@tamagui/lucide-icons-2';
import { Paragraph, SizableText, Text, XStack, YStack } from 'tamagui';

import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { SettingsGroup, SettingsGroupLabel } from '@/components/settings-section-parts';
import { formatStorageSize } from '@/domain/storage-usage';
import { describeVaultEntries, describeVaultHint, vaultFillPercent } from '@/domain/vault-usage';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';
import { useVaultUsage } from '@/hooks/use-vault-usage';

export function VaultSettingsGroup() {
  const theme = useTheme();
  const scale = useTextScale();
  const { clear, error, ready, usage } = useVaultUsage();
  const percent = vaultFillPercent(usage);
  const empty = usage.entries <= 0;

  const confirmClear = () => {
    Alert.alert(
      'Vaciar el vault',
      `Se eliminarán ${describeVaultEntries(usage)} (${formatStorageSize(usage.bytes)}) que custodias para otros usuarios y todavía no se han entregado. Esta acción no se puede deshacer.`,
      [
        { style: 'cancel', text: 'Cancelar' },
        { onPress: () => { void clear(); }, style: 'destructive', text: 'Vaciar vault' },
      ],
    );
  };

  return (
    <YStack gap={10}>
      <SettingsGroupLabel icon={<ShieldCheck color={theme.placeholder} size={15} />} title="Vault de mensajes">Mensajes cifrados que custodias temporalmente para contactos desconectados.</SettingsGroupLabel>
      <SettingsGroup>
        <YStack gap={12} padding={16}>
          <XStack alignItems="baseline" gap={8}>
            <SizableText color={theme.text} fontSize={scale(24)} fontWeight="700">{formatStorageSize(usage.bytes)}</SizableText>
            <Text color={theme.placeholder} flex={1} fontSize={scale(12)}>{ready ? describeVaultEntries(usage) : 'Calculando…'}</Text>
          </XStack>
          <View style={[styles.track, { backgroundColor: theme.backgroundSelected }]}>
            <View style={[styles.fill, { backgroundColor: theme.accent, width: `${percent}%` }]} />
          </View>
          <Paragraph color={theme.placeholder} fontSize={scale(11)}>{describeVaultHint(usage)}</Paragraph>
          {error ? <Paragraph accessibilityRole="alert" backgroundColor={theme.dangerSoft} borderColor={theme.dangerBorder} borderRadius={10} borderWidth={1} color={theme.onDangerSoft} fontSize={scale(12)} padding={10}>{error}</Paragraph> : null}
          <NativeRipplePressable
            accessibilityLabel="Vaciar el vault"
            accessibilityRole="button"
            accessibilityState={{ disabled: empty }}
            disabled={empty}
            onPress={confirmClear}
            style={[styles.clear, { backgroundColor: empty ? theme.backgroundElement : theme.dangerSoft, opacity: empty ? 0.6 : 1 }]}
          >
            <Trash2 color={empty ? theme.placeholder : theme.onDangerSoft} size={16} />
            <Text color={empty ? theme.placeholder : theme.onDangerSoft} fontSize={scale(13)} fontWeight="600">Vaciar vault</Text>
          </NativeRipplePressable>
        </YStack>
      </SettingsGroup>
    </YStack>
  );
}

const styles = StyleSheet.create({
  clear: { alignItems: 'center', alignSelf: 'flex-start', borderRadius: 10, flexDirection: 'row', gap: 8, overflow: 'hidden', paddingHorizontal: 14, paddingVertical: 9 },
  fill: { borderRadius: 4, height: 6 },
  track: { borderRadius: 4, height: 6, overflow: 'hidden' },
});
