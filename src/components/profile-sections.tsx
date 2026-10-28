import { StyleSheet, View } from 'react-native';
import { BadgeAlert, Check, Cloud, Copy, Info, Pencil, QrCode, Shield } from '@tamagui/lucide-icons-2';
import { Input, Progress, SizableText, Text, XStack, YStack } from 'tamagui';

import type { UPeerIdentity } from '@/domain/identity-record';
import { getTrustWarning } from '@/domain/trust';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';
import { CopyField } from '@/components/copy-field';
import { SettingsGroup } from '@/components/settings-section-parts';
import { NativeRippleButton } from '@/components/native-ripple-button';
import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { ProfileAvatar } from '@/components/profile-avatar';

export function ProfileHeroSection({ identity, alias, avatar, busy, onAliasChange, onAliasSave, onAvatarPress }: {
  identity: UPeerIdentity;
  alias: string;
  avatar?: string;
  busy: boolean;
  onAliasChange: (value: string) => void;
  onAliasSave: () => void;
  onAvatarPress: () => void;
}) {
  const theme = useTheme();
  const scale = useTextScale();
  return (
    <XStack alignItems="center" backgroundColor={theme.backgroundElement} gap={24} paddingHorizontal={20} paddingVertical={24}>
      <NativeRipplePressable accessibilityLabel="Cambiar foto de perfil" accessibilityRole="button" disabled={busy} onPress={onAvatarPress} style={styles.avatar}>
        <ProfileAvatar avatar={avatar} name={alias || identity.upeerId} />
        <View style={[styles.avatarEditBadge, { backgroundColor: theme.accent, borderColor: theme.backgroundElement }]}><Pencil color={theme.onAccent} size={12} /></View>
      </NativeRipplePressable>
      <YStack flex={1} gap={5} minWidth={0}>
        <SizableText color={theme.placeholder} fontSize={scale(11)} fontWeight="600" letterSpacing={1}>NOMBRE</SizableText>
        <XStack alignItems="center" gap={8}>
          <Input accessibilityLabel="Nombre visible" autoCapitalize="words" backgroundColor={theme.background} borderColor={theme.border} color={theme.text} flex={1} maxLength={64} onChangeText={onAliasChange} onSubmitEditing={onAliasSave} placeholder="Escribe tu nombre..." placeholderTextColor={theme.placeholder} value={alias} />
          <NativeRippleButton accessibilityLabel="Guardar nombre" backgroundColor={theme.accent} circular disabled={busy} icon={<Check color={theme.onAccent} size={18} />} onPress={onAliasSave} />
        </XStack>
        <Text color={theme.placeholder} fontFamily="$mono" fontSize={scale(11)}>{identity.upeerId.slice(0, 24)}…</Text>
      </YStack>
    </XStack>
  );
}

export function ProfileStatsSection({ reputation, vaultStats, reputationUnavailable = false, vaultUnavailable = false, onInfo }: {
  reputation: { vouchScore: number; connectionCount: number } | null;
  vaultStats: { count: number; sizeBytes: number } | null;
  reputationUnavailable?: boolean;
  vaultUnavailable?: boolean;
  onInfo: (key: 'reputacion' | 'almacenamiento') => void;
}) {
  const theme = useTheme();
  const scale = useTextScale();
  const vouchScore = reputation?.vouchScore;
  const connectionCount = reputation?.connectionCount;
  const hasReputation = vouchScore !== undefined && connectionCount !== undefined;
  const connections = connectionCount ?? 0;
  const warning = getTrustWarning(vouchScore);
  const usageGb = (vaultStats?.sizeBytes ?? 0) / 1_073_741_824;
  return (
    <YStack paddingHorizontal={16}>
      <SettingsGroup>
        <YStack gap={10} padding={16}>
          <XStack alignItems="center" gap={8}>
            <Shield color={theme.placeholder} size={15} />
            <SizableText color={theme.text} flexShrink={1} fontSize={scale(14)} fontWeight="600" numberOfLines={1}>Reputación</SizableText>
            <YStack flex={1} />
            <NativeRipplePressable accessibilityLabel="¿Qué es la reputación?" onPress={() => onInfo('reputacion')}><Info color={theme.placeholder} size={16} /></NativeRipplePressable>
          </XStack>
          {warning ? (
            <XStack alignItems="flex-start" gap={8}>
              <BadgeAlert color={theme.danger} size={15} strokeWidth={2.3} />
              <Text color={theme.danger} flex={1} fontSize={scale(12)} fontWeight="600">{warning}</Text>
            </XStack>
          ) : null}
          <Text color={theme.placeholder} fontSize={scale(12)}>{reputationUnavailable ? 'No disponible en este cliente' : !hasReputation ? 'Cargando...' : `${connections} ${connections === 1 ? 'contacto directo' : 'contactos directos'} en tu red. La reputación es un criterio interno que solo se avisa cuando es muy mala.`}</Text>
        </YStack>
  
        <YStack gap={10} padding={16}>
          <XStack alignItems="center" gap={8}>
            <Cloud color={theme.placeholder} size={15} />
            <SizableText color={theme.text} flexShrink={1} fontSize={scale(14)} fontWeight="600" numberOfLines={1}>Almacenamiento cedido</SizableText>
            <YStack flex={1} />
            <NativeRipplePressable accessibilityLabel="¿Qué es el almacenamiento cedido?" onPress={() => onInfo('almacenamiento')}><Info color={theme.placeholder} size={16} /></NativeRipplePressable>
          </XStack>
          {vaultStats ? <>
            <XStack alignItems="baseline" justifyContent="space-between"><SizableText color={theme.text} fontSize={scale(13)} fontWeight="600">{formatBytes(vaultStats.sizeBytes)}</SizableText><Text color={theme.placeholder} fontSize={scale(10)}>usados</Text></XStack>
            <Progress backgroundColor={theme.border} value={Math.min(usageGb * 100, 100)}><Progress.Indicator backgroundColor={usageGb > 0.8 ? theme.accent : theme.placeholder} /></Progress>
            <XStack>
              <YStack flex={1}><Text color={theme.placeholder} fontSize={scale(10)} textTransform="uppercase">Archivos P2P</Text><SizableText color={theme.text} fontSize={scale(13)} fontWeight="600">{vaultStats.count}</SizableText></YStack>
              <YStack flex={1}><Text color={theme.placeholder} fontSize={scale(10)} textTransform="uppercase">Cuota dinámica</Text><SizableText color={theme.text} fontSize={scale(13)} fontWeight="600">Activa</SizableText></YStack>
            </XStack>
          </> : <Text color={theme.placeholder} fontSize={scale(12)}>{vaultUnavailable ? 'No disponible en este cliente' : 'Cargando...'}</Text>}
        </YStack>
      </SettingsGroup>
    </YStack>
  );
}

export function ProfileAddressSection({ fullId, onCopy, onShowQr, onInfo }: {
  fullId: string;
  onCopy: () => void;
  onShowQr: () => void;
  onInfo: () => void;
}) {
  const theme = useTheme();
  const scale = useTextScale();
  return (
    <YStack gap={12} paddingHorizontal={16} paddingVertical={20}>
      <XStack alignItems="flex-start" gap={4}>
        <YStack flex={1} gap={4}>
          <SizableText color={theme.text} fontSize={scale(15)} fontWeight="700">Tu dirección de contacto</SizableText>
          <Text color={theme.placeholder} fontSize={scale(12)}>Compártela para que puedan escribirte sin usar tu frase de recuperación.</Text>
        </YStack>
        <NativeRipplePressable accessibilityLabel="¿Qué es la dirección de contacto?" onPress={onInfo} style={{ padding: 6 }}><Info color={theme.placeholder} size={16} /></NativeRipplePressable>
      </XStack>
      <CopyField accessibilityLabel="Copiar dirección de contacto" onCopy={onCopy} value={fullId} />
      <XStack gap={8}>
        <NativeRippleButton backgroundColor="transparent" borderColor={theme.border} borderWidth={1} color={theme.text} icon={<QrCode color={theme.placeholder} size={15} />} onPress={onShowQr}>Mostrar QR</NativeRippleButton>
        <NativeRippleButton backgroundColor="transparent" borderColor={theme.border} borderWidth={1} color={theme.text} icon={<Copy color={theme.placeholder} size={15} />} onPress={onCopy}>Copiar</NativeRippleButton>
      </XStack>
    </YStack>
  );
}

export function ProfileTechnicalSection({ identity, address, onInfo, onCopy }: {
  identity: UPeerIdentity;
  address: string;
  onInfo: (key: 'id' | 'clave' | 'red') => void;
  onCopy: (value: string) => void;
}) {
  const theme = useTheme();
  return (
    <YStack paddingHorizontal={16} paddingVertical={12}>
      <TechnicalField label="ID de contacto" value={identity.upeerId} onInfo={() => onInfo('id')} onCopy={onCopy} />
      <View style={[styles.divider, { backgroundColor: theme.border }]} />
      <TechnicalField label="Clave de verificación" value={identity.publicKey} onInfo={() => onInfo('clave')} onCopy={onCopy} />
      <View style={[styles.divider, { backgroundColor: theme.border }]} />
      <TechnicalField label="Dirección de red" value={address} onInfo={() => onInfo('red')} onCopy={onCopy} />
    </YStack>
  );
}

function TechnicalField({ label, value, onInfo, onCopy }: { label: string; value: string; onInfo: () => void; onCopy: (value: string) => void }) {
  const theme = useTheme();
  const scale = useTextScale();
  return (
    <YStack gap={5} paddingVertical={10}>
      <XStack alignItems="center" gap={3}><SizableText color={theme.placeholder} flex={1} fontSize={scale(10)} fontWeight="600" letterSpacing={0.7}>{label.toUpperCase()}</SizableText><NativeRipplePressable accessibilityLabel={`¿Qué es ${label}?`} onPress={onInfo}><Info color={theme.placeholder} size={15} /></NativeRipplePressable></XStack>
      <CopyField accessibilityLabel={`Copiar ${label}`} onCopy={() => onCopy(value)} value={value} />
    </YStack>
  );
}

function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB'];
  const unitIndex = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  return `${(bytes / 1024 ** unitIndex).toFixed(1)} ${units[unitIndex]}`;
}

const styles = StyleSheet.create({ avatar: { borderRadius: 8, height: 96, overflow: 'hidden', position: 'relative', width: 96 }, avatarEditBadge: { alignItems: 'center', borderRadius: 8, borderWidth: 2, bottom: 5, height: 24, justifyContent: 'center', position: 'absolute', right: 5, width: 24 }, divider: { height: StyleSheet.hairlineWidth, width: '100%' } });