import type { ReactNode } from 'react';
import { Alert, StyleSheet } from 'react-native';
import { Database, FolderOpen, HardDrive, Paperclip, RefreshCw, Trash2, UserRound } from '@tamagui/lucide-icons-2';
import { Paragraph, SizableText, Text, XStack, YStack } from 'tamagui';

import { NativeRippleButton } from '@/components/native-ripple-button';
import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { SettingsGroup, SettingsGroupLabel, SettingsNote, SettingsSectionHeader } from '@/components/settings-section-parts';
import { describeAttachmentCount, formatStorageSize } from '@/domain/storage-usage';
import { useStorageUsage } from '@/hooks/use-storage-usage';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

export function StorageSettingsSection() {
  const theme = useTheme();
  const scale = useTextScale();
  const { clearCache, error, ready, refresh, usage } = useStorageUsage();

  const confirmClearCache = () => {
    Alert.alert(
      'Limpiar la caché',
      `Se liberarán ${formatStorageSize(usage.cacheBytes)} de imágenes y archivos temporales. Se volverán a generar cuando haga falta; tus mensajes y los archivos recibidos no se tocan.`,
      [{ text: 'Cancelar', style: 'cancel' }, { text: 'Limpiar', style: 'destructive', onPress: clearCache }],
    );
  };

  return <YStack gap={12}>
    <SettingsSectionHeader icon={<HardDrive color={theme.accent} size={18} />} title="Almacenamiento">Los mensajes y los archivos se guardan en este teléfono, no en un servidor.</SettingsSectionHeader>

    <SettingsGroupLabel title="En este dispositivo">Lo que ocupan tus mensajes, archivos y ajustes.</SettingsGroupLabel>
    <SettingsGroup>
      <YStack gap={16} padding={16}>
        <XStack alignItems="baseline" gap={8}>
          <SizableText color={theme.text} fontSize={scale(30)} fontWeight="700">{formatStorageSize(usage.ownBytes)}</SizableText>
          <Text color={theme.placeholder} flex={1} fontSize={scale(12)}>{describeAttachmentCount(usage.attachmentCount)}</Text>
        </XStack>
        {error ? <Paragraph accessibilityRole="alert" backgroundColor={theme.dangerSoft} borderColor={theme.dangerBorder} borderRadius={10} borderWidth={1} color={theme.onDangerSoft} fontSize={scale(12)} padding={10}>{error}</Paragraph> : null}
        <YStack gap={12}>
          <BreakdownRow icon={<Database color={theme.placeholder} size={17} />} label="Base de datos" hint="Contactos, ajustes y preferencias" value={formatStorageSize(usage.databaseBytes)} />
          <BreakdownRow icon={<Paperclip color={theme.placeholder} size={17} />} label="Archivos recibidos" hint="Fotos, vídeos, notas de voz y documentos" value={formatStorageSize(usage.attachmentsBytes)} />
          <BreakdownRow icon={<UserRound color={theme.placeholder} size={17} />} label="Avatares" hint="Tu foto y las de tus contactos" value={formatStorageSize(usage.avatarsBytes)} />
        </YStack>
      </YStack>
      <NativeRipplePressable accessibilityLabel="Actualizar el uso de disco" accessibilityRole="button" disabled={!ready} onPress={refresh} style={styles.row}>
        <RefreshCw color={theme.placeholder} size={17} />
        <SizableText color={theme.text} flex={1} fontSize={scale(14)} fontWeight="500">Actualizar</SizableText>
        <Text color={theme.placeholder} fontSize={scale(12)}>{ready ? 'Al día' : 'Calculando…'}</Text>
      </NativeRipplePressable>
    </SettingsGroup>

    <SettingsGroupLabel icon={<FolderOpen color={theme.accent} size={15} />} title="Caché">Imágenes y archivos temporales que se pueden borrar sin perder nada.</SettingsGroupLabel>
    <SettingsGroup>
      <YStack gap={12} padding={16}>
        <XStack alignItems="baseline" gap={8}>
          <SizableText color={theme.text} fontSize={scale(20)} fontWeight="600">{formatStorageSize(usage.cacheBytes)}</SizableText>
          <Text color={theme.placeholder} flex={1} fontSize={scale(12)}>entre miniaturas, descargas y copias temporales</Text>
        </XStack>
        <NativeRippleButton
          backgroundColor="transparent"
          borderColor={theme.border}
          borderWidth={1}
          color={theme.danger}
          disabled={!ready || usage.cacheBytes === 0}
          height={46}
          icon={<Trash2 color={usage.cacheBytes === 0 ? theme.placeholder : theme.danger} size={17} />}
          onPress={confirmClearCache}
          opacity={usage.cacheBytes === 0 ? 0.5 : 1}
        >Limpiar la caché</NativeRippleButton>
      </YStack>
    </SettingsGroup>

    <SettingsNote>uPeer no tiene servidor: si borras la aplicación, estos datos desaparecen con ella. La caché no suma en el uso de arriba porque se puede regenerar en cualquier momento.</SettingsNote>
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
  row: { alignItems: 'center', flexDirection: 'row', gap: 12, minHeight: 56, paddingHorizontal: 16, paddingVertical: 12 },
});
