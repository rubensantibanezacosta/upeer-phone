import { useState } from 'react';
import { ActivityIndicator } from 'react-native';
import { ArrowRight, QrCode, ShieldCheck, X } from '@tamagui/lucide-icons-2';
import { Card, Paragraph, Separator, SizableText, TextArea, XStack, YStack } from 'tamagui';

import { CopyField } from '@/components/copy-field';
import { NativeRippleButton } from '@/components/native-ripple-button';
import { QrScannerSheet } from '@/components/qr-scanner-sheet';
import { useCopyToClipboard } from '@/hooks/use-copy-to-clipboard';
import { useDeviceEnrollment } from '@/hooks/use-device-enrollment';
import type { EnrollmentCertificateSummary } from '../../modules/upeer-transport/src/UPeerTransport.types';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

type ScanTarget = 'invite' | 'response' | null;

export function DevicePairingPanel({ onPaired }: { onPaired?: (summary: EnrollmentCertificateSummary) => void } = {}) {
  const theme = useTheme();
  const scale = useTextScale();
  const copyToClipboard = useCopyToClipboard();
  const { state, linkAccount, finish, cancel } = useDeviceEnrollment();
  const [inviteCode, setInviteCode] = useState('');
  const [responseCode, setResponseCode] = useState('');
  const [scanTarget, setScanTarget] = useState<ScanTarget>(null);

  const handleFinish = async () => {
    const installed = await finish(responseCode);
    if (installed) onPaired?.(installed);
  };

  const handleScanned = (value: string) => {
    setScanTarget(null);
    if (scanTarget === 'response') {
      setResponseCode(value);
      return;
    }
    setInviteCode(value);
    void linkAccount(value).then((installed) => {
      if (installed) onPaired?.(installed);
    });
  };

  const startLink = async () => {
    const installed = await linkAccount(inviteCode);
    if (installed) onPaired?.(installed);
  };

  const waiting = state.phase === 'sending' || state.phase === 'waiting';
  const manual = state.phase === 'manual';

  return <YStack gap={16}>
    {state.installed ? <Card backgroundColor={theme.successSoft} borderColor={theme.success} borderRadius={14} borderWidth={1} gap={10} padding={16}>
      <XStack alignItems="center" gap={8}><ShieldCheck color={theme.success} size={20} /><SizableText color={theme.text} fontSize={scale(16)} fontWeight="700">Ya estás en tu cuenta</SizableText></XStack>
      <Paragraph color={theme.placeholder} fontSize={scale(13)}>Este dispositivo forma parte de la cuenta y abre la sesión sin la frase de recuperación.</Paragraph>
      <YStack gap={4}>
        <InfoRow label="Cuenta" theme={theme} value={state.installed.accountUpeerId} />
        <InfoRow label="Dispositivo" theme={theme} value={state.installed.deviceId} />
        <InfoRow label="Caduca" theme={theme} value={new Date(state.installed.expiresAt).toLocaleDateString()} />
      </YStack>
    </Card> : null}

    {!state.installed && !waiting && !manual ? <YStack gap={12}>
      <Paragraph color={theme.placeholder} fontSize={scale(13)}>Abre uPeer en tu otro dispositivo, entra en Configuración → Dispositivos y genera una invitación. Escanéala aquí: el resto ocurre solo.</Paragraph>
      <NativeRippleButton backgroundColor={theme.accent} color={theme.onAccent} disabled={state.busy} fontWeight="700" height={50} icon={<QrCode color={theme.onAccent} size={18} />} onPress={() => setScanTarget('invite')}>Escanear el QR de la invitación</NativeRippleButton>
      <Separator borderColor="border-color" />
      <TextArea accessibilityLabel="Código de invitación" autoCapitalize="none" autoCorrect={false} backgroundColor="color-2" borderColor="border-color" color="color" height={82} onChangeText={setInviteCode} placeholder="O pega el código de invitación" placeholderTextColor="placeholder-color" textAlignVertical="top" value={inviteCode} />
      <NativeRippleButton backgroundColor={theme.accentSoft} color={theme.text} disabled={state.busy || inviteCode.trim().length === 0} height={46} icon={<ArrowRight color={theme.onAccentSoft} size={17} />} onPress={() => void startLink()}>{state.busy ? 'Preparando…' : 'Entrar en mi cuenta'}</NativeRippleButton>
    </YStack> : null}

    {waiting ? <Card backgroundColor={theme.backgroundElement} borderColor={theme.border} borderRadius={14} borderWidth={1} gap={10} padding={16}>
      <XStack alignItems="center" gap={10}><ActivityIndicator color={theme.accent} size="small" /><SizableText color={theme.text} flex={1} fontSize={scale(15)} fontWeight="700">{state.phase === 'sending' ? 'Enviando la solicitud…' : 'Esperando a tu cuenta…'}</SizableText></XStack>
      <Paragraph color={theme.placeholder} fontSize={scale(13)}>{state.phase === 'sending' ? 'Este dispositivo se está presentando al que tiene la cuenta.' : 'El dispositivo que autoriza emite el certificado en cuanto recibe la solicitud. No hace falta tocar nada más.'}</Paragraph>
      {state.offer ? <YStack gap={4}>
        <InfoRow label="Cuenta" theme={theme} value={state.offer.accountUpeerId} />
        <InfoRow label="Autoriza" theme={theme} value={state.offer.authorizerAddress ?? state.offer.authorizerDeviceId} />
      </YStack> : null}
      <NativeRippleButton backgroundColor="transparent" borderColor={theme.border} borderWidth={1} color={theme.textSecondary} height={42} icon={<X color={theme.textSecondary} size={16} />} onPress={() => { setInviteCode(''); void cancel(); }}>Cancelar</NativeRippleButton>
    </Card> : null}

    {manual ? <YStack gap={12}>
      <Paragraph color={theme.placeholder} fontSize={scale(13)}>Este dispositivo no puede alcanzar al que autoriza. Entrega este código a mano y trae la respuesta del certificado.</Paragraph>
      <CopyField accessibilityLabel="Copiar solicitud de emparejamiento" onCopy={() => void copyToClipboard(state.requestCode, 'Solicitud copiada')} value={state.requestCode} />
      {state.offer ? <YStack gap={4}>
        <InfoRow label="Cuenta" theme={theme} value={state.offer.accountUpeerId} />
        <InfoRow label="Caduca" theme={theme} value={new Date(state.offer.expiresAt).toLocaleString()} />
      </YStack> : null}
      <Separator borderColor="border-color" />
      <TextArea accessibilityLabel="Código de respuesta" autoCapitalize="none" autoCorrect={false} backgroundColor="color-2" borderColor="border-color" color="color" height={92} onChangeText={setResponseCode} placeholder="Pega el código de respuesta" placeholderTextColor="placeholder-color" textAlignVertical="top" value={responseCode} />
      <NativeRippleButton backgroundColor={theme.accentSoft} color={theme.text} disabled={state.busy} height={46} icon={<QrCode color={theme.onAccentSoft} size={17} />} onPress={() => setScanTarget('response')}>Escanear QR de respuesta</NativeRippleButton>
      <NativeRippleButton backgroundColor={theme.accent} color={theme.onAccent} disabled={state.busy} fontWeight="700" height={46} icon={<ShieldCheck color={theme.onAccent} size={17} />} onPress={() => void handleFinish()}>{state.busy ? 'Verificando…' : 'Terminar vinculación'}</NativeRippleButton>
      <NativeRippleButton backgroundColor="transparent" borderColor={theme.border} borderWidth={1} color={theme.textSecondary} height={42} icon={<X color={theme.textSecondary} size={16} />} onPress={() => { setInviteCode(''); setResponseCode(''); void cancel(); }}>Cancelar</NativeRippleButton>
    </YStack> : null}

    {!!state.error && <Paragraph accessibilityRole="alert" backgroundColor={theme.dangerSoft} borderColor={theme.dangerBorder} borderRadius={10} borderWidth={1} color={theme.onDangerSoft} padding={12}>{state.error}</Paragraph>}

    <QrScannerSheet
      onClose={() => setScanTarget(null)}
      onScanned={handleScanned}
      title={scanTarget === 'response' ? 'Escanear respuesta' : 'Escanear invitación'}
      visible={scanTarget !== null}
    />
    <QrScannerSheet
      onClose={() => setScanTarget(null)}
      onScanned={handleScanned}
      title={scanTarget === 'response' ? 'Escanear respuesta' : 'Escanear invitación'}
      visible={scanTarget !== null}
    />
  </YStack>;
}

function InfoRow({ label, value, theme }: { label: string; value: string; theme: ReturnType<typeof useTheme> }) {
  const scale = useTextScale();
  return <YStack gap={1}>
    <SizableText color={theme.placeholder} fontSize={scale(10)} fontWeight="600" letterSpacing={0.6}>{label.toUpperCase()}</SizableText>
    <SizableText color={theme.textSecondary} fontFamily="monospace" fontSize={scale(12)} numberOfLines={2}>{value}</SizableText>
  </YStack>;
}
