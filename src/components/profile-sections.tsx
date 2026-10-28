import { StyleSheet, View } from 'react-native';
import { BadgeAlert, Check, Cloud, Copy, Fingerprint, Info, Pencil, QrCode, Shield } from '@tamagui/lucide-icons-2';
import { Input, SizableText, Text, XStack, YStack } from 'tamagui';

import type { UPeerIdentity } from '@/domain/identity-record';
import { formatStorageSize } from '@/domain/storage-usage';
import { getTrustWarning } from '@/domain/trust';
import { describeVaultEntries, describeVaultHint, type VaultUsage, vaultFillPercent } from '@/domain/vault-usage';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';
import { CopyField } from '@/components/copy-field';
import { SettingsGroup, SettingsGroupLabel } from '@/components/settings-section-parts';
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
    <SettingsGroup>
      <XStack alignItems="center" gap={16} padding={16}>
        <NativeRipplePressable accessibilityLabel="Cambiar foto de perfil" accessibilityRole="button" disabled={busy} onPress={onAvatarPress} style={styles.avatar}>
          <View style={styles.avatarClip}><ProfileAvatar avatar={avatar} name={alias || identity.upeerId} /></View>
          <View style={[styles.avatarEditBadge, { backgroundColor: theme.accent, borderColor: theme.backgroundElement }]}><Pencil color={theme.onAccent} size={11} /></View>
        </NativeRipplePressable>
        <YStack flex={1} gap={6} minWidth={0}>
          <SizableText color={theme.placeholder} fontSize={scale(10)} fontWeight="600" letterSpacing={0.6}>NOMBRE</SizableText>
          <XStack alignItems="center" gap={8}>
            <Input accessibilityLabel="Nombre visible" autoCapitalize="words" backgroundColor={theme.background} borderColor={theme.border} color={theme.text} flex={1} maxLength={64} onChangeText={onAliasChange} onSubmitEditing={onAliasSave} placeholder="Escribe tu nombre..." placeholderTextColor={theme.placeholder} value={alias} />
            <NativeRippleButton accessibilityLabel="Guardar nombre" backgroundColor={theme.accent} circular disabled={busy} icon={<Check color={theme.onAccent} size={18} />} onPress={onAliasSave} />
          </XStack>
          <Text color={theme.placeholder} fontFamily="$mono" fontSize={scale(11)} numberOfLines={1}>{identity.upeerId.slice(0, 24)}…</Text>
        </YStack>
      </XStack>
    </SettingsGroup>
  );
}

export function ProfileStatsSection({ reputation, vaultUsage, vaultReady = false, onInfo }: {
  reputation: { vouchScore: number; connectionCount: number } | null;
  vaultUsage: VaultUsage;
  vaultReady?: boolean;
  onInfo: (key: 'reputacion' | 'almacenamiento') => void;
}) {
  const theme = useTheme();
  const scale = useTextScale();
  const vouchScore = reputation?.vouchScore;
  const connectionCount = reputation?.connectionCount;
  const hasReputation = vouchScore !== undefined && connectionCount !== undefined;
  const connections = connectionCount ?? 0;
  const warning = getTrustWarning(vouchScore);
  return (
    <YStack gap={10}>
      <SettingsGroupLabel action={<InfoButton label="¿Qué es la reputación?" onPress={() => onInfo('reputacion')} />} icon={<Shield color={theme.placeholder} size={15} />} title="Reputación" />
      <SettingsGroup>
        <YStack gap={10} padding={16}>
          {warning ? (
            <XStack alignItems="flex-start" gap={8}>
              <BadgeAlert color={theme.danger} size={15} strokeWidth={2.3} />
              <Text color={theme.danger} flex={1} fontSize={scale(12)} fontWeight="600">{warning}</Text>
            </XStack>
          ) : null}
          <Text color={theme.placeholder} fontSize={scale(12)}>{!hasReputation ? 'Leyendo tu red de contactos…' : `${connections} ${connections === 1 ? 'contacto directo' : 'contactos directos'} en tu red. La reputación es un criterio interno que solo se avisa cuando es muy mala.`}</Text>
        </YStack>
      </SettingsGroup>

      <SettingsGroupLabel action={<InfoButton label="¿Qué es el almacenamiento cedido?" onPress={() => onInfo('almacenamiento')} />} icon={<Cloud color={theme.placeholder} size={15} />} title="Almacenamiento cedido">Mensajes cifrados que custodias para contactos desconectados.</SettingsGroupLabel>
      <SettingsGroup>
        <YStack gap={10} padding={16}>
          <XStack alignItems="baseline" gap={8}>
            <SizableText color={theme.text} fontSize={scale(24)} fontWeight="700">{formatStorageSize(vaultUsage.bytes)}</SizableText>
            <Text color={theme.placeholder} flex={1} fontSize={scale(12)}>{vaultReady ? describeVaultEntries(vaultUsage) : 'Calculando…'}</Text>
          </XStack>
          <View style={[styles.track, { backgroundColor: theme.backgroundSelected }]}>
            <View style={[styles.fill, { backgroundColor: theme.accent, width: `${vaultFillPercent(vaultUsage)}%` }]} />
          </View>
          <Text color={theme.placeholder} fontSize={scale(12)}>{vaultReady ? describeVaultHint(vaultUsage) : 'Leyendo el vault de este dispositivo…'}</Text>
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
  return (
    <YStack gap={10}>
      <SettingsGroupLabel action={<InfoButton label="¿Qué es la dirección de contacto?" onPress={onInfo} />} icon={<Fingerprint color={theme.placeholder} size={15} />} title="Tu dirección de contacto">Compártela para que puedan escribirte sin usar tu frase de recuperación.</SettingsGroupLabel>
      <SettingsGroup>
        <YStack gap={12} padding={16}>
          <CopyField accessibilityLabel="Copiar dirección de contacto" onCopy={onCopy} value={fullId} />
          <XStack gap={8}>
            <NativeRippleButton backgroundColor="transparent" borderColor={theme.border} borderWidth={1} color={theme.text} icon={<QrCode color={theme.placeholder} size={15} />} onPress={onShowQr}>Mostrar QR</NativeRippleButton>
            <NativeRippleButton backgroundColor="transparent" borderColor={theme.border} borderWidth={1} color={theme.text} icon={<Copy color={theme.placeholder} size={15} />} onPress={onCopy}>Copiar</NativeRippleButton>
          </XStack>
        </YStack>
      </SettingsGroup>
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
    <YStack gap={10}>
      <SettingsGroupLabel icon={<Fingerprint color={theme.placeholder} size={15} />} title="Datos técnicos" />
      <SettingsGroup>
        <TechnicalField label="ID de contacto" value={identity.upeerId} onInfo={() => onInfo('id')} onCopy={onCopy} />
        <TechnicalField label="Clave de verificación" value={identity.publicKey} onInfo={() => onInfo('clave')} onCopy={onCopy} />
        <TechnicalField label="Dirección de red" value={address} onInfo={() => onInfo('red')} onCopy={onCopy} />
      </SettingsGroup>
    </YStack>
  );
}

function TechnicalField({ label, value, onInfo, onCopy }: { label: string; value: string; onInfo: () => void; onCopy: (value: string) => void }) {
  const theme = useTheme();
  const scale = useTextScale();
  return (
    <YStack gap={5} padding={16}>
      <XStack alignItems="center" gap={4}>
        <SizableText color={theme.placeholder} flex={1} fontSize={scale(10)} fontWeight="600" letterSpacing={0.6}>{label.toUpperCase()}</SizableText>
        <InfoButton label={`¿Qué es ${label}?`} onPress={onInfo} />
      </XStack>
      <CopyField accessibilityLabel={`Copiar ${label}`} onCopy={() => onCopy(value)} value={value} />
    </YStack>
  );
}

function InfoButton({ label, onPress }: { label: string; onPress: () => void }) {
  const theme = useTheme();
  return <NativeRipplePressable accessibilityLabel={label} accessibilityRole="button" onPress={onPress} style={styles.info}><Info color={theme.placeholder} size={16} /></NativeRipplePressable>;
}

const styles = StyleSheet.create({
  avatar: { height: 96, position: 'relative', width: 96 },
  avatarClip: { borderRadius: 48, height: 96, overflow: 'hidden', width: 96 },
  avatarEditBadge: { alignItems: 'center', borderRadius: 11, borderWidth: 2, bottom: 0, height: 22, justifyContent: 'center', position: 'absolute', right: 0, width: 22 },
  fill: { borderRadius: 4, height: 6 },
  info: { alignItems: 'center', borderRadius: 14, height: 28, justifyContent: 'center', overflow: 'hidden', width: 28 },
  track: { borderRadius: 4, height: 6, overflow: 'hidden' },
});
