import { useState } from 'react';
import { StyleSheet, View } from 'react-native';
import QRCode from 'react-native-qrcode-svg';
import { ArrowRight, QrCode, ShieldCheck, Smartphone, Trash2 } from '@tamagui/lucide-icons-2';
import { Paragraph, Separator, SizableText, Text, TextArea, XStack, YStack } from 'tamagui';

import { BottomSheet } from '@/components/bottom-sheet';
import { CopyField } from '@/components/copy-field';
import { NativeRippleButton } from '@/components/native-ripple-button';
import { QrScannerSheet } from '@/components/qr-scanner-sheet';
import { useCopyToClipboard } from '@/hooks/use-copy-to-clipboard';
import { useEnrollmentAuthorizer } from '@/hooks/use-enrollment-authorizer';
import { useTheme } from '@/hooks/use-theme';

type DeviceInviteSheetProps = {
  visible: boolean;
  onClose: () => void;
  onAuthorized: () => void;
};

export function DeviceInviteSheet({ visible, onClose, onAuthorized }: DeviceInviteSheetProps) {
  const theme = useTheme();
  const copyToClipboard = useCopyToClipboard();
  const { state, start, accept, issue, cancel, reset } = useEnrollmentAuthorizer();
  const [requestCode, setRequestCode] = useState('');
  const [scanning, setScanning] = useState(false);

  const runAccept = (code: string) => {
    setScanning(false);
    setRequestCode(code);
    void accept(code);
  };

  const finish = () => {
    onAuthorized();
    void reset();
    onClose();
  };

  return <>
    <BottomSheet icon={<Smartphone color={theme.accent} size={20} />} onClose={onClose} title="Vincular dispositivo" visible={visible}>
      {state.issued
        ? <YStack gap={12}>
          <XStack alignItems="center" gap={8}><ShieldCheck color={theme.success} size={20} /><SizableText color={theme.text} fontSize={15} fontWeight="700">Dispositivo autorizado</SizableText></XStack>
          <YStack gap={6}>
            <InfoRow label="Dispositivo" theme={theme} value={state.issued.deviceId} />
            <InfoRow label="Plataforma" theme={theme} value={state.issued.platform} />
            <InfoRow label="Vinculado" theme={theme} value={dateLabel(state.issued.issuedAt)} />
          </YStack>
          <Paragraph color={theme.placeholder} fontSize={12}>Entrega este código al dispositivo nuevo para que instale su certificado.</Paragraph>
          <CopyField accessibilityLabel="Copiar respuesta del certificado" onCopy={() => void copyToClipboard(state.issued!.responseCode, 'Respuesta copiada')} truncate value={state.issued.responseCode} />
          <QrCanvas theme={theme} value={state.issued.responseCode} />
          <NativeRippleButton backgroundColor={theme.accent} color={theme.onAccent} fontWeight="700" height={46} icon={<ArrowRight color={theme.onAccent} size={17} />} onPress={finish}>Listo</NativeRippleButton>
        </YStack>
        : <YStack gap={12}>
          {state.invite
            ? <YStack gap={10}>
              <XStack alignItems="center" gap={8}><Smartphone color={theme.accent} size={18} /><SizableText color={theme.text} fontSize={14} fontWeight="700">Invitación activa</SizableText></XStack>
              <Paragraph color={theme.placeholder} fontSize={12}>Muéstrala o cópiala en el dispositivo que quieres vincular. Caduca en {minutesLeft(state.invite.expiresAt)} min y solo sirve una vez.</Paragraph>
              <CopyField accessibilityLabel="Copiar invitación" onCopy={() => void copyToClipboard(state.invite!.code, 'Invitación copiada')} truncate value={state.invite.code} />
              <QrCanvas theme={theme} value={state.invite.code} />
              <NativeRippleButton accessibilityLabel="Descartar invitación" backgroundColor="transparent" color={theme.textSecondary} height={38} icon={<Trash2 color={theme.textSecondary} size={15} />} onPress={() => void cancel(state.invite!.inviteId)}>Descartar invitación</NativeRippleButton>
            </YStack>
            : <YStack gap={10}>
              <Paragraph color={theme.placeholder} fontSize={12}>Genera una invitación y ábrela en el dispositivo nuevo. No necesitas compartir tu frase de recuperación: tú autorizas y el otro dispositivo solo recibe su certificado.</Paragraph>
              <NativeRippleButton backgroundColor={theme.accent} color={theme.onAccent} disabled={state.busy} fontWeight="700" height={46} icon={<Smartphone color={theme.onAccent} size={17} />} onPress={() => void start()}>{state.busy ? 'Generando…' : 'Generar invitación'}</NativeRippleButton>
            </YStack>}

          {state.accepted
            ? <YStack borderColor={theme.border} borderRadius={12} borderWidth={1} gap={10} padding={12}>
              <XStack alignItems="center" gap={8}><ShieldCheck color={theme.accent} size={18} /><SizableText color={theme.text} fontSize={14} fontWeight="700">Solicitud aceptada</SizableText></XStack>
              <YStack gap={6}>
                <InfoRow label="Dispositivo" theme={theme} value={state.accepted.deviceId} />
                <InfoRow label="Plataforma" theme={theme} value={state.accepted.platform} />
                <InfoRow label="Capacidades" theme={theme} value={state.accepted.capabilities.join(', ') || '—'} />
              </YStack>
              <NativeRippleButton backgroundColor={theme.accent} color={theme.onAccent} disabled={state.busy} fontWeight="700" height={46} icon={<ShieldCheck color={theme.onAccent} size={17} />} onPress={() => void issue()}>{state.busy ? 'Firmando…' : 'Emitir certificado'}</NativeRippleButton>
            </YStack>
            : <YStack gap={10}>
              {state.invite ? <Separator borderColor={theme.border} /> : null}
              <Paragraph color={theme.placeholder} fontSize={12}>Cuando el otro dispositivo te muestre su solicitud, escanéala o pégala aquí.</Paragraph>
              <NativeRippleButton backgroundColor={theme.accentSoft} color={theme.text} disabled={state.busy} height={44} icon={<QrCode color={theme.onAccentSoft} size={17} />} onPress={() => setScanning(true)}>Escanear solicitud</NativeRippleButton>
              <TextArea accessibilityLabel="Código de solicitud" autoCapitalize="none" autoCorrect={false} backgroundColor="color-2" borderColor="border-color" color="color" height={88} onChangeText={setRequestCode} placeholder="Pega el código de solicitud" placeholderTextColor="placeholder-color" textAlignVertical="top" value={requestCode} />
              <NativeRippleButton backgroundColor="transparent" borderColor={theme.border} borderWidth={1} color={theme.text} disabled={state.busy || !requestCode.trim()} height={42} icon={<ArrowRight color={theme.textSecondary} size={16} />} onPress={() => runAccept(requestCode)}>Aceptar solicitud</NativeRippleButton>
            </YStack>}

          {state.invites.length > (state.invite ? 1 : 0) && !state.issued
            ? <YStack gap={6}>
              <SizableText color={theme.placeholder} fontSize={10} fontWeight="600" letterSpacing={0.7}>OTRAS INVITACIONES</SizableText>
              {state.invites.filter((invite) => invite.inviteId !== state.invite?.inviteId).map((invite) => <XStack alignItems="center" borderColor={theme.border} borderRadius={10} borderWidth={1} gap={10} key={invite.inviteId} padding={10}>
                <YStack flex={1} gap={2}>
                  <Text color={theme.textSecondary} fontFamily="monospace" fontSize={11} numberOfLines={1}>{shortId(invite.inviteId)}</Text>
                  <Text color={theme.placeholder} fontSize={11}>Caduca en {minutesLeft(invite.expiresAt)} min</Text>
                </YStack>
                <NativeRippleButton accessibilityLabel="Descartar invitación" backgroundColor="transparent" height={32} icon={<Trash2 color={theme.danger} size={15} />} onPress={() => void cancel(invite.inviteId)} />
              </XStack>)}
            </YStack>
            : null}

          {!!state.error && <Paragraph accessibilityRole="alert" backgroundColor={theme.dangerSoft} borderColor={theme.dangerBorder} borderRadius={10} borderWidth={1} color={theme.onDangerSoft} fontSize={12} padding={10}>{state.error}</Paragraph>}
        </YStack>}
    </BottomSheet>

    <QrScannerSheet onClose={() => setScanning(false)} onScanned={runAccept} title="Escanear solicitud" visible={scanning} />
  </>;
}

function QrCanvas({ theme, value }: { theme: ReturnType<typeof useTheme>; value: string }) {
  return <View style={[styles.qrCanvas, { backgroundColor: theme.qrSurface, borderColor: theme.border }]}>
    <QRCode ecl="L" size={172} value={value} />
  </View>;
}

function InfoRow({ label, theme, value }: { label: string; theme: ReturnType<typeof useTheme>; value: string }) {
  return <YStack gap={1}>
    <SizableText color={theme.placeholder} fontSize={10} fontWeight="600" letterSpacing={0.6}>{label.toUpperCase()}</SizableText>
    <SizableText color={theme.textSecondary} fontFamily="monospace" fontSize={12} numberOfLines={1} ellipsizeMode="middle">{value}</SizableText>
  </YStack>;
}

function minutesLeft(expiresAt: number): number {
  return Math.max(0, Math.ceil((expiresAt - Date.now()) / 60000));
}

function dateLabel(value: number): string {
  if (!Number.isFinite(value) || value <= 0) return '—';
  return new Date(value).toLocaleDateString('es-ES', { day: 'numeric', hour: '2-digit', minute: '2-digit', month: 'short' });
}

function shortId(value: string): string {
  return value.length <= 20 ? value : `${value.slice(0, 12)}…${value.slice(-6)}`;
}

const styles = StyleSheet.create({
  qrCanvas: { alignItems: 'center', alignSelf: 'center', borderRadius: 12, borderWidth: StyleSheet.hairlineWidth, padding: 10 },
});
