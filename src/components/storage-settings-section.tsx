import type { ReactNode } from 'react';
import { StyleSheet } from 'react-native';
import { HardDrive, MessageSquare, Monitor, Paperclip, RefreshCw } from '@tamagui/lucide-icons-2';
import { Paragraph, SizableText, Text, XStack, YStack } from 'tamagui';

import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { SettingsGroup, SettingsGroupLabel, SettingsSectionHeader } from '@/components/settings-section-parts';
import { describeSharedFiles, formatStorageSize } from '@/domain/storage-usage';
import { useStorageUsage } from '@/hooks/use-storage-usage';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

export function StorageSettingsSection() {
  const theme = useTheme();
  const scale = useTextScale();
  const { error, ready, refresh, usage } = useStorageUsage();

  return <YStack gap={12}>
    <SettingsSectionHeader
      action={<NativeRipplePressable accessibilityLabel="Actualizar datos" accessibilityRole="button" onPress={refresh} style={styles.action}>
        <RefreshCw color={theme.placeholder} size={18} />
      </NativeRipplePressable>}
      icon={<HardDrive color={theme.accent} size={18} />}
      title="Almacenamiento"
    >Qué ocupa uPeer en este dispositivo.</SettingsSectionHeader>

    <SettingsGroupLabel icon={<Monitor color={theme.placeholder} size={15} />} title="En este dispositivo">Mensajes y archivos que uPeer guarda en este teléfono.</SettingsGroupLabel>
    <SettingsGroup>
      <YStack gap={16} padding={16}>
        <XStack alignItems="baseline" gap={8}>
          <SizableText color={theme.text} fontSize={scale(30)} fontWeight="700">{formatStorageSize(usage.ownBytes)}</SizableText>
          <Text color={theme.placeholder} flex={1} fontSize={scale(12)}>{ready ? 'en este teléfono' : 'Calculando…'}</Text>
        </XStack>
        {error ? <Paragraph accessibilityRole="alert" backgroundColor={theme.dangerSoft} borderColor={theme.dangerBorder} borderRadius={10} borderWidth={1} color={theme.onDangerSoft} fontSize={scale(12)} padding={10}>{error}</Paragraph> : null}
        <YStack gap={12}>
          <BreakdownRow icon={<MessageSquare color={theme.placeholder} size={17} />} label="Conversaciones e identidad" hint="Base de datos local" value={formatStorageSize(usage.databaseBytes)} />
          <BreakdownRow icon={<Paperclip color={theme.placeholder} size={17} />} label="Archivos compartidos" hint={describeSharedFiles(usage.attachmentCount)} value={formatStorageSize(usage.attachmentsBytes)} />
        </YStack>
      </YStack>
    </SettingsGroup>
  </YStack>;
}

function BreakdownRow({ icon, label, hint, value }: { icon: ReactNode; label: string; hint: string; value: string }) {
  const theme = useTheme();
  const scale = useTextScale();
  return <XStack alignItems="center" gap={10}>
    {icon}
    <YStack flex={1} gap={2}>
      <SizableText color={theme.text} fontSize={scale(14)} fontWeight="500">{label}</SizableText>
      <Text color={theme.placeholder} fontSize={scale(11)}>{hint}</Text>
    </YStack>
    <Text color={theme.text} fontSize={scale(13)} fontWeight="600">{value}</Text>
  </XStack>;
}

const styles = StyleSheet.create({
  action: { alignItems: 'center', borderRadius: 18, height: 36, justifyContent: 'center', overflow: 'hidden', width: 36 },
});
