import { useState } from 'react';
import { Info, Smartphone, UserPlus } from '@tamagui/lucide-icons-2';
import { Card, Paragraph, SizableText, Text, XStack, YStack } from 'tamagui';

import { DeviceInviteSheet } from '@/components/device-invite-sheet';
import { NativeRippleButton } from '@/components/native-ripple-button';
import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { useAccountDevices } from '@/hooks/use-account-devices';
import { useTheme } from '@/hooks/use-theme';

export function DeviceAuthorizerPanel({ onInfo }: { onInfo: () => void }) {
  const theme = useTheme();
  const { state, refresh } = useAccountDevices();
  const [sheetVisible, setSheetVisible] = useState(false);

  return <YStack borderBottomColor={theme.border} borderBottomWidth={1} gap={12} paddingHorizontal={20} paddingVertical={20}>
    <XStack alignItems="center" gap={8}>
      <Smartphone color={theme.placeholder} size={16} />
      <SizableText color={theme.text} flex={1} fontSize={14} fontWeight="600">Dispositivos de la cuenta</SizableText>
      <NativeRipplePressable accessibilityLabel="¿Qué son los dispositivos de la cuenta?" onPress={onInfo}><Info color={theme.placeholder} size={16} /></NativeRipplePressable>
    </XStack>
    <Text color={theme.placeholder} fontSize={12}>Estos dispositivos pueden usar tu cuenta sin tu frase de recuperación</Text>

    <YStack gap={8}>
      {state.loading && state.devices.length === 0
        ? <Paragraph color={theme.placeholder} fontSize={13}>Leyendo dispositivos autorizados…</Paragraph>
        : state.devices.map((device) => <DeviceRow
          authorizedAt={device.authorizedAt}
          current={device.deviceId === state.localDeviceId}
          key={device.deviceId}
          platform={device.platform}
          theme={theme}
          value={device.deviceId}
        />)}
      {!state.loading && state.devices.length === 0
        ? <Paragraph color={theme.placeholder} fontSize={13}>Todavía no hay dispositivos autorizados en esta cuenta.</Paragraph>
        : null}
    </YStack>

    {state.error ? <Paragraph accessibilityRole="alert" backgroundColor={theme.dangerSoft} borderColor={theme.dangerBorder} borderRadius={10} borderWidth={1} color={theme.onDangerSoft} fontSize={12} padding={10}>{state.error}</Paragraph> : null}

    <NativeRippleButton backgroundColor={theme.accentSoft} color={theme.text} height={46} icon={<UserPlus color={theme.onAccentSoft} size={18} />} onPress={() => setSheetVisible(true)}>Vincular dispositivo</NativeRippleButton>

    <DeviceInviteSheet onAuthorized={() => void refresh()} onClose={() => setSheetVisible(false)} visible={sheetVisible} />
  </YStack>;
}

function DeviceRow({ authorizedAt, current, platform, theme, value }: { authorizedAt: number; current: boolean; platform?: string; theme: ReturnType<typeof useTheme>; value: string }) {
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
