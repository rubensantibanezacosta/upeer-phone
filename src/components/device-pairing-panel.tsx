import { useState } from 'react';
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
  const { state, begin, finish, cancel } = useDeviceEnrollment();
  const [inviteCode, setInviteCode] = useState('');
  const [responseCode, setResponseCode] = useState('');
  const [scanTarget, setScanTarget] = useState<ScanTarget>(null);

  const handleFinish = async () => {
    const installed = await finish(responseCode);
    if (installed) onPaired?.(installed);
  };

  const handleScanned = (value: string) => {
    if (scanTarget === 'invite') setInviteCode(value);
    if (scanTarget === 'response') setResponseCode(value);
    setScanTarget(null);
  };

  return <YStack gap={16}>
    {state.installed ? <Card backgroundColor={theme.successSoft} borderColor={theme.success} borderRadius={14} borderWidth={1} gap={10} padding={16}>
      <XStack alignItems="center" gap={8}><ShieldCheck color={theme.success} size={20} /><SizableText color={theme.text} fontSize={scale(16)} fontWeight="700">Dispositivo vinculado</SizableText></XStack>
      <Paragraph color={theme.placeholder} fontSize={scale(13)}>Este dispositivo ya forma parte de la cuenta y abre la sesión sin la frase de recuperación.</Paragraph>
      <YStack gap={4}>
        <InfoRow label="Cuenta" theme={theme} value={state.installed.accountUpeerId} />
        <InfoRow label="Dispositivo" theme={theme} value={state.installed.deviceId} />
        <InfoRow label="Caduca" theme={theme} value={new Date(state.installed.expiresAt).toLocaleDateString()} />
      </YStack>
    </Card> : null}

    {!state.installed && !state.offer ? <YStack gap={12}>
      <Paragraph color={theme.placeholder} fontSize={scale(13)}>Si tienes otro dispositivo con la cuenta abierta, ábrelo en Configuración → Dispositivos, genera una invitación y escanéala aquí. También puedes pegar el código a mano.</Paragraph>
      <TextArea accessibilityLabel="Código de invitación" autoCapitalize="none" autoCorrect={false} backgroundColor="color-2" borderColor="border-color" color="color" height={92} onChangeText={setInviteCode} placeholder="Pega el código de invitación" placeholderTextColor="placeholder-color" textAlignVertical="top" value={inviteCode} />
      <NativeRippleButton backgroundColor={theme.accentSoft} color={theme.text} disabled={state.busy} height={46} icon={<QrCode color={theme.onAccentSoft} size={17} />} onPress={() => setScanTarget('invite')}>Escanear QR de invitación</NativeRippleButton>
      <NativeRippleButton backgroundColor={theme.accent} color={theme.onAccent} disabled={state.busy} fontWeight="700" height={46} icon={<ArrowRight color={theme.onAccent} size={17} />} onPress={() => void begin(inviteCode)}>{state.busy ? 'Preparando…' : 'Continuar'}</NativeRippleButton>
    </YStack> : null}

    {state.offer ? <YStack gap={12}>
      <Paragraph color={theme.placeholder} fontSize={scale(13)}>Entrega este código al dispositivo autorizado y trae de vuelta la respuesta del certificado.</Paragraph>
      <CopyField accessibilityLabel="Copiar solicitud de emparejamiento" onCopy={() => void copyToClipboard(state.requestCode, 'Solicitud copiada')} value={state.requestCode} />
      <YStack gap={4}>
        <InfoRow label="Cuenta" theme={theme} value={state.offer.accountUpeerId} />
        <InfoRow label="Caduca" theme={theme} value={new Date(state.offer.expiresAt).toLocaleString()} />
      </YStack>
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
  </YStack>;
}

function InfoRow({ label, value, theme }: { label: string; value: string; theme: ReturnType<typeof useTheme> }) {
  const scale = useTextScale();
  return <YStack gap={1}>
    <SizableText color={theme.placeholder} fontSize={scale(10)} fontWeight="600" letterSpacing={0.6}>{label.toUpperCase()}</SizableText>
    <SizableText color={theme.textSecondary} fontFamily="monospace" fontSize={scale(12)} numberOfLines={2}>{value}</SizableText>
  </YStack>;
}
