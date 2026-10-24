import { StyleSheet, View } from 'react-native';
import { Check, Cloud, Copy, Fingerprint, Info, Pencil, QrCode, Shield } from '@tamagui/lucide-icons-2';
import { Input, Progress, SizableText, Text, XStack, YStack } from 'tamagui';

import type { UPeerIdentity } from '@/domain/identity';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';
import { CopyField } from '@/components/copy-field';
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
    <XStack alignItems="center" backgroundColor={theme.backgroundElement} borderBottomColor={theme.border} borderBottomWidth={1} gap={24} paddingHorizontal={20} paddingVertical={24}>
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

export function ProfileStatsSection({ reputation, vaultStats, onInfo }: {
  reputation: { vouchScore: number; connectionCount: number } | null;
  vaultStats: { count: number; sizeBytes: number } | null;
  onInfo: (key: 'reputacion' | 'almacenamiento') => void;
}) {
  const theme = useTheme();
  const scale = useTextScale();
  const vouchScore = reputation?.vouchScore;
  const connectionCount = reputation?.connectionCount;
  const hasReputation = vouchScore !== undefined && connectionCount !== undefined;
  const score = vouchScore ?? 0;
  const connections = connectionCount ?? 0;
  const isNew = score === 50 && connections === 0;
  const reputationLabel = !hasReputation ? 'Cargando…' : isNew ? 'Sin historial' : score >= 80 ? 'Alta' : score >= 65 ? 'Buena' : score < 40 ? 'Baja' : 'Estándar';
  const scoreColor = score >= 80 ? theme.accent : score < 40 ? theme.placeholder : theme.textSecondary;
  const usageGb = (vaultStats?.sizeBytes ?? 0) / 1_073_741_824;
  return (
    <XStack borderBottomColor={theme.border} borderBottomWidth={1}>
      <YStack borderRightColor={theme.border} borderRightWidth={1} flex={1} gap={13} paddingHorizontal={20} paddingVertical={20}>
        <XStack alignItems="center" justifyContent="space-between">
          <XStack alignItems="center" gap={7}><Shield color={theme.placeholder} size={15} /><SizableText color={theme.placeholder} fontSize={scale(11)} fontWeight="700" letterSpacing={0.7}>REPUTACIÓN</SizableText></XStack>
          <XStack alignItems="center" gap={4}>{hasReputation ? <Text color={theme.textSecondary} fontSize={scale(10)} fontWeight="600">{reputationLabel}</Text> : null}<NativeRipplePressable accessibilityLabel="¿Qué es la reputación?" onPress={() => onInfo('reputacion')}><Info color={theme.placeholder} size={16} /></NativeRipplePressable></XStack>
        </XStack>
        {hasReputation ? <><Progress backgroundColor={theme.border} value={score}><Progress.Indicator backgroundColor={scoreColor} /></Progress><XStack>
          <YStack flex={1}><Text color={theme.placeholder} fontSize={scale(10)} textTransform="uppercase">Vouches</Text><SizableText color={theme.text} fontSize={scale(13)} fontWeight="600">{score}/100</SizableText></YStack>
          <YStack flex={1}><Text color={theme.placeholder} fontSize={scale(10)} textTransform="uppercase">Contactos</Text><SizableText color={theme.text} fontSize={scale(13)} fontWeight="600">{connections}</SizableText></YStack>
        </XStack></> : <Text color={theme.placeholder} fontSize={scale(12)}>Cargando...</Text>}
      </YStack>
      <YStack flex={1} gap={13} paddingHorizontal={20} paddingVertical={20}>
        <XStack alignItems="center" justifyContent="space-between"><XStack alignItems="center" gap={7}><Cloud color={theme.placeholder} size={15} /><SizableText color={theme.placeholder} fontSize={scale(11)} fontWeight="700" letterSpacing={0.5}>ALMAC. CEDIDO</SizableText></XStack><NativeRipplePressable accessibilityLabel="¿Qué es el almacenamiento cedido?" onPress={() => onInfo('almacenamiento')}><Info color={theme.placeholder} size={16} /></NativeRipplePressable></XStack>
        {vaultStats ? <><XStack alignItems="baseline" justifyContent="space-between"><SizableText color={theme.text} fontSize={scale(13)} fontWeight="600">{formatBytes(vaultStats.sizeBytes)}</SizableText><Text color={theme.placeholder} fontSize={scale(10)}>usados</Text></XStack><Progress backgroundColor={theme.border} value={Math.min(usageGb * 100, 100)}><Progress.Indicator backgroundColor={usageGb > 0.8 ? theme.accent : theme.placeholder} /></Progress><XStack><YStack flex={1}><Text color={theme.placeholder} fontSize={scale(9)} textTransform="uppercase">Archivos P2P</Text><SizableText color={theme.text} fontSize={scale(13)} fontWeight="600">{vaultStats.count}</SizableText></YStack><YStack flex={1}><Text color={theme.placeholder} fontSize={scale(9)} textTransform="uppercase">Cuota dinámica</Text><SizableText color={theme.text} fontSize={scale(13)} fontWeight="600">Activa</SizableText></YStack></XStack></> : <Text color={theme.placeholder} fontSize={scale(12)}>Cargando...</Text>}
      </YStack>
    </XStack>
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
    <YStack borderBottomColor={theme.border} borderBottomWidth={1} gap={12} paddingHorizontal={20} paddingVertical={20}>
      <XStack alignItems="center" gap={8}><Fingerprint color={theme.placeholder} size={16} /><SizableText color={theme.text} flex={1} fontSize={scale(14)} fontWeight="600">Tu dirección de contacto</SizableText><NativeRipplePressable accessibilityLabel="¿Qué es la dirección de contacto?" onPress={onInfo}><Info color={theme.placeholder} size={16} /></NativeRipplePressable></XStack>
      <Text color={theme.placeholder} fontSize={scale(12)}>Comparte esta dirección para que puedan escribirte</Text>
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
    <YStack paddingHorizontal={20} paddingVertical={12}>
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