import { CircleCheck, EyeOff, Lock, ShieldCheck } from '@tamagui/lucide-icons-2';
import { Paragraph, SizableText, Text, XStack, YStack } from 'tamagui';

import { BottomSheet } from '@/components/bottom-sheet';
import { CopyField } from '@/components/copy-field';
import { useCopyToClipboard } from '@/hooks/use-copy-to-clipboard';
import { useTheme } from '@/hooks/use-theme';
import type { PeerSecurity } from '../../../modules/upeer-transport/src/UPeerTransport.types';

type ChatCipherSheetProps = {
  alias: string;
  error: string;
  loading: boolean;
  onClose: () => void;
  security: PeerSecurity | null;
  visible: boolean;
};

export function ChatCipherSheet({ alias, error, loading, onClose, security, visible }: ChatCipherSheetProps) {
  const theme = useTheme();
  const copyToClipboard = useCopyToClipboard();
  const session = security?.hasSession === true;

  return (
    <BottomSheet icon={<Lock color={theme.accent} size={20} />} onClose={onClose} title="Cifrado" visible={visible}>
      <YStack alignItems="center" gap={8} paddingTop={4}>
        <YStack alignItems="center" backgroundColor={theme.successSoft} borderRadius={32} height={64} justifyContent="center" width={64}>
          <Lock color={theme.success} size={30} />
        </YStack>
        <SizableText color={theme.text} fontSize={16} fontWeight="700" textAlign="center">Los mensajes están protegidos</SizableText>
        <Text color={theme.placeholder} fontSize={12} textAlign="center">
          Todo lo que escribes a {alias} se cifra en tu dispositivo antes de salir. Solo el suyo puede descifrarlo.
        </Text>
      </YStack>

      <YStack gap={12}>
        <SecurityPoint
          description="Ni uPeer, ni tu proveedor de internet, ni ningún intermediario puede leer tus mensajes."
          icon={EyeOff}
          title="Nadie puede espiar"
        />
        <SecurityPoint
          description="El cifrado se activa solo con el primer mensaje. No tienes que hacer nada."
          icon={ShieldCheck}
          title="Solo entre vosotros dos"
        />
        <SecurityPoint
          description={`Compara el número de seguridad con ${alias} por otro canal para descartar un impostor.`}
          icon={CircleCheck}
          title="Puedes comprobarlo"
        />
      </YStack>

      {loading ? <Paragraph color={theme.placeholder} fontSize={12}>Leyendo la sesión cifrada…</Paragraph> : null}
      {error ? <Paragraph accessibilityRole="alert" backgroundColor={theme.dangerSoft} borderColor={theme.dangerBorder} borderRadius={10} borderWidth={1} color={theme.onDangerSoft} fontSize={12} padding={10}>{error}</Paragraph> : null}

      {security ? (
        <YStack gap={8}>
          <Text color={theme.placeholder} fontSize={12} fontWeight="600">Número de seguridad</Text>
          {security.safetyNumber
            ? <>
              <CopyField accessibilityLabel="Copiar número de seguridad" onCopy={() => { void copyToClipboard(security.safetyNumber, 'Número de seguridad copiado'); }} value={security.safetyNumber} />
              <Text color={theme.placeholder} fontSize={11}>Ambos dispositivos calculan el mismo número. Si coincide, no hay nadie en medio.</Text>
            </>
            : <Text color={theme.placeholder} fontSize={12}>Todavía no hay clave del contacto para comparar.</Text>}
          <XStack alignItems="center" gap={6}>
            <YStack backgroundColor={session ? theme.successSoft : theme.backgroundSelected} borderRadius={4} height={7} width={7} />
            <Text color={theme.textSecondary} fontSize={12}>{session ? 'Sesión de doble ratchet activa' : 'Sin sesión establecida todavía'}</Text>
          </XStack>
        </YStack>
      ) : null}
    </BottomSheet>
  );
}

function SecurityPoint({ description, icon: Icon, title }: { description: string; icon: typeof Lock; title: string }) {
  const theme = useTheme();
  return (
    <XStack alignItems="flex-start" gap={12}>
      <YStack alignItems="center" backgroundColor={theme.backgroundElement} borderRadius={10} height={36} justifyContent="center" width={36}>
        <Icon color={theme.placeholder} size={19} />
      </YStack>
      <YStack flex={1} gap={2}>
        <Text color={theme.text} fontSize={13} fontWeight="600">{title}</Text>
        <Text color={theme.placeholder} fontSize={11}>{description}</Text>
      </YStack>
    </XStack>
  );
}
