import { useCallback, useState } from 'react';
import { Alert } from 'react-native';
import { Ban, Info, ShieldAlert, Smartphone, Trash2, UserPlus } from '@tamagui/lucide-icons-2';
import { Card, Paragraph, SizableText, Text, XStack, YStack } from 'tamagui';

import { DeviceInviteSheet } from '@/components/device-invite-sheet';
import { DevicePairingPanel } from '@/components/device-pairing-panel';
import { BottomSheet } from '@/components/bottom-sheet';
import { NativeRippleButton } from '@/components/native-ripple-button';
import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { useAccountDevices } from '@/hooks/use-account-devices';
import { useTheme } from '@/hooks/use-theme';
import { revokeAccountDevice } from '../../modules/upeer-transport/src/UPeerTransportModule';

export function DeviceAuthorizerPanel({ onInfo }: { onInfo?: () => void }) {
  const theme = useTheme();
  const { state, refresh } = useAccountDevices();
  const [sheetVisible, setSheetVisible] = useState(false);
  const [pairingVisible, setPairingVisible] = useState(false);
  const [busyId, setBusyId] = useState('');
  const [notice, setNotice] = useState('');

  const revoke = useCallback((deviceId: string, platform?: string) => {
    Alert.alert(
      'Revocar dispositivo',
      `Se quitará el acceso de ${platformLabel(platform)} a tu cuenta. Dejará de recibir mensajes nuevos y tendrá que vincularse otra vez con una invitación.`,
      [
        { text: 'Cancelar', style: 'cancel' },
        {
          text: 'Revocar',
          style: 'destructive',
          onPress: () => {
            setBusyId(deviceId);
            setNotice('');
            revokeAccountDevice(deviceId, 'revocado desde el móvil')
              .then((status) => {
                const epoch = status.epoch ? ` · registro v${status.epoch}` : '';
                setNotice(status.ready ? `Dispositivo revocado; ya no recibe mensajes${epoch}` : 'Revocación registrada, registro pendiente');
                return refresh();
              })
              .catch((cause: unknown) => {
                console.warn('No se pudo revocar el dispositivo.', cause);
                setNotice('No se pudo revocar el dispositivo');
              })
              .finally(() => setBusyId(''));
          },
        },
      ],
    );
  }, [refresh]);

  return <YStack borderBottomColor={theme.border} borderBottomWidth={1} gap={12} paddingHorizontal={20} paddingVertical={20}>
    <XStack alignItems="center" gap={8}>
      <Smartphone color={theme.placeholder} size={16} />
      <SizableText color={theme.text} flex={1} fontSize={14} fontWeight="600">Dispositivos de la cuenta</SizableText>
      {onInfo ? <NativeRipplePressable accessibilityLabel="¿Qué son los dispositivos de la cuenta?" onPress={onInfo}><Info color={theme.placeholder} size={16} /></NativeRipplePressable> : null}
    </XStack>
    <Text color={theme.placeholder} fontSize={12}>Estos dispositivos pueden usar tu cuenta sin tu frase de recuperación</Text>

    {state.localAdmitted ? null : <YStack gap={8}>
      <XStack alignItems="center" backgroundColor={theme.dangerSoft} borderColor={theme.dangerBorder} borderRadius={10} borderWidth={1} gap={8} padding={10}>
        <ShieldAlert color={theme.onDangerSoft} size={16} />
        <Text color={theme.onDangerSoft} flex={1} fontSize={12}>Este dispositivo fue revocado y ya no puede enviar ni recibir mensajes de la cuenta.</Text>
      </XStack>
      <NativeRippleButton backgroundColor={theme.accentSoft} color={theme.text} height={44} icon={<UserPlus color={theme.onAccentSoft} size={16} />} onPress={() => setPairingVisible(true)}>Volver a vincular este dispositivo</NativeRippleButton>
    </YStack>}

    <YStack gap={8}>
      {state.loading && state.devices.length === 0
        ? <Paragraph color={theme.placeholder} fontSize={13}>Leyendo dispositivos autorizados…</Paragraph>
        : state.devices.map((device) => <DeviceRow
          authorizedAt={device.authorizedAt}
          busy={busyId === device.deviceId}
          current={device.deviceId === state.localDeviceId}
          key={device.deviceId}
          onRevoke={() => revoke(device.deviceId, device.platform)}
          platform={device.platform}
          theme={theme}
          value={device.deviceId}
        />)}
      {!state.loading && state.devices.length === 0
        ? <Paragraph color={theme.placeholder} fontSize={13}>{state.ready ? 'Todavía no hay dispositivos autorizados en esta cuenta.' : 'El registro de la cuenta aún no está listo en este dispositivo.'}</Paragraph>
        : null}
    </YStack>

    {state.error ? <Paragraph accessibilityRole="alert" backgroundColor={theme.dangerSoft} borderColor={theme.dangerBorder} borderRadius={10} borderWidth={1} color={theme.onDangerSoft} fontSize={12} padding={10}>{state.error}</Paragraph> : null}
    {notice ? <Text accessibilityLiveRegion="polite" color={notice.startsWith('No se pudo') ? theme.danger : theme.textSecondary} fontSize={12}>{notice}</Text> : null}

    <NativeRippleButton backgroundColor={theme.accentSoft} color={theme.text} height={46} icon={<UserPlus color={theme.onAccentSoft} size={18} />} onPress={() => setSheetVisible(true)}>Vincular dispositivo</NativeRippleButton>

    {state.revoked.length > 0 ? <YStack gap={6}>
      <XStack alignItems="center" gap={6}>
        <Ban color={theme.placeholder} size={13} />
        <Text color={theme.placeholder} fontSize={11} fontWeight="600">Revocados</Text>
      </XStack>
      {state.revoked.slice(0, 3).map((revocation) => <YStack gap={1} key={revocation.deviceId} opacity={0.6}>
        <XStack alignItems="center" gap={8}>
          <Text color={theme.placeholder} flex={1} fontFamily="monospace" fontSize={11} numberOfLines={1}>{revocation.deviceId}</Text>
          <Text color={theme.placeholder} fontSize={11}>{authorizedLabel(revocation.revokedAt)}</Text>
        </XStack>
        {revocation.reason ? <Text color={theme.placeholder} fontSize={10} numberOfLines={1}>{revocation.reason}</Text> : null}
      </YStack>)}
    </YStack> : null}

    <DeviceInviteSheet onAuthorized={() => void refresh()} onClose={() => setSheetVisible(false)} visible={sheetVisible} />
    <BottomSheet icon={<Smartphone color={theme.accent} size={20} />} onClose={() => { setPairingVisible(false); void refresh(); }} title="Vincular este dispositivo" visible={pairingVisible}>
      <Paragraph color={theme.placeholder} fontSize={12}>Pide a otro dispositivo de tu cuenta una invitación nueva. Al terminar, cierra y abre la sesión otra vez con tu frase de recuperación.</Paragraph>
      <DevicePairingPanel />
    </BottomSheet>
  </YStack>;
}

function DeviceRow({ authorizedAt, busy, current, onRevoke, platform, theme, value }: { authorizedAt: number; busy: boolean; current: boolean; onRevoke: () => void; platform?: string; theme: ReturnType<typeof useTheme>; value: string }) {
  return <Card backgroundColor={theme.backgroundElement} borderColor={current ? theme.accent : theme.border} borderRadius={12} borderWidth={1} padding={12}>
    <XStack alignItems="center" gap={10}>
      <YStack flex={1} gap={2}>
        <XStack alignItems="center" gap={6}>
          <SizableText color={theme.text} fontSize={13} fontWeight="600">{platformLabel(platform)}</SizableText>
          {current ? <YStack backgroundColor={theme.accentSoft} borderRadius={6} paddingHorizontal={6} paddingVertical={1}><Text color={theme.onAccentSoft} fontSize={10} fontWeight="700">ESTE DISPOSITIVO</Text></YStack> : null}
        </XStack>
        <Text color={theme.placeholder} fontFamily="monospace" fontSize={11} numberOfLines={1}>{value}</Text>
      </YStack>
      <YStack alignItems="flex-end" gap={1}>
        <Text color={theme.placeholder} fontSize={9} fontWeight="600" letterSpacing={0.6}>VINCULADO</Text>
        <Text color={theme.textSecondary} fontSize={11}>{authorizedLabel(authorizedAt)}</Text>
      </YStack>
      {current ? null : <NativeRipplePressable accessibilityLabel={`Revocar ${platformLabel(platform)}`} onPress={onRevoke} style={{ opacity: busy ? 0.4 : 1 }}>
        <Trash2 color={theme.danger} size={17} />
      </NativeRipplePressable>}
    </XStack>
  </Card>;
}

function authorizedLabel(authorizedAt: number): string {
  if (!Number.isFinite(authorizedAt) || authorizedAt <= 0) return '';
  const date = new Date(authorizedAt);
  const sameYear = date.getFullYear() === new Date().getFullYear();
  return date.toLocaleDateString('es-ES', sameYear ? { day: 'numeric', month: 'short' } : { day: 'numeric', month: 'short', year: 'numeric' });
}

function platformLabel(platform?: string): string {
  if (!platform) return 'Dispositivo';
  const normalized = platform.toLowerCase();
  if (normalized.includes('android')) return 'Android';
  if (normalized.includes('ios')) return 'iOS';
  if (normalized.includes('linux')) return 'Linux';
  if (normalized.includes('darwin') || normalized.includes('mac')) return 'macOS';
  if (normalized.includes('win')) return 'Windows';
  return platform;
}
