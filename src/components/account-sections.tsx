import { useEffect, useState } from 'react';
import { Modal, ScrollView, StyleSheet, View } from 'react-native';
import { ImageManipulator, SaveFormat } from 'expo-image-manipulator';
import * as ImagePicker from 'expo-image-picker';
import QRCode from 'react-native-qrcode-svg';
import { Clock3, Copy, LogOut, X } from '@tamagui/lucide-icons-2';
import { H1, Paragraph, SizableText, Text, XStack, YStack } from 'tamagui';

import type { UPeerIdentity } from '@/domain/identity-record';
import { useCopyToClipboard } from '@/hooks/use-copy-to-clipboard';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';
import { NativeRippleButton } from '@/components/native-ripple-button';
import { confirmSignOut } from '@/components/confirm-sign-out';
import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { loadProfile, saveProfile } from '@/storage/identity-store';
import { DeviceAuthorizerPanel } from '@/components/device-authorizer-panel';
import { InfoDialog } from '@/components/info-dialog';
import { profileHelpContent, type ProfileInfoKey } from '@/components/profile-help-content';
import { ProfileAddressSection, ProfileHeroSection, ProfileStatsSection, ProfileTechnicalSection } from '@/components/profile-sections';

export function CallsScreen() {
  const theme = useTheme();
  const scale = useTextScale();
  return (
    <YStack flex={1} backgroundColor={theme.background}>
      <ScrollView contentContainerStyle={styles.callsContent}>
        <XStack alignItems="center" justifyContent="space-between">
          <H1 color={theme.text} fontSize={scale(24)} fontWeight="700">Llamadas</H1>
        </XStack>
        <YStack alignItems="center" flex={1} gap={12} justifyContent="center" padding={24}>
          <YStack alignItems="center" backgroundColor={theme.backgroundElement} borderRadius={32} height={64} justifyContent="center" width={64}>
            <Clock3 color={theme.placeholder} size={28} /></YStack>
          <SizableText color={theme.text} fontSize={scale(17)} fontWeight="600">Aún no hay llamadas</SizableText>
          <Paragraph color={theme.placeholder} textAlign="center">El historial y las llamadas estarán disponibles cuando se implemente esta función en la app móvil.</Paragraph>
        </YStack>
      </ScrollView>
    </YStack>
  );
}

export function ProfileScreen({ identity, address, onProfileUpdated, onSignOut }: { identity: UPeerIdentity; address: string; onProfileUpdated: () => void; onSignOut: () => void }) {
  const theme = useTheme();
  const scale = useTextScale();
  const [alias, setAlias] = useState('');
  const [avatar, setAvatar] = useState<string>();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [qrOpen, setQrOpen] = useState(false);
  const copyToClipboard = useCopyToClipboard();
  const [info, setInfo] = useState<ProfileInfoKey | null>(null);
  const fullId = `${identity.upeerId}@${address}`;

  useEffect(() => {
    loadProfile().then((profile) => { setAlias(profile?.alias ?? ''); setAvatar(profile?.avatar); }).catch((cause: unknown) => {
      setError(cause instanceof Error ? cause.message : 'No se pudo cargar el perfil.');
    });
  }, []);

  const saveAlias = async () => {
    const nextAlias = alias.trim().slice(0, 64);
    if (!nextAlias) {
      setError('Escribe un nombre antes de guardarlo.');
      return;
    }
    setBusy(true);
    try {
      await saveProfile({ upeerId: identity.upeerId, publicKey: identity.publicKey, alias: nextAlias });
      setAlias(nextAlias);
      setError('');
      onProfileUpdated();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'No se pudo guardar el perfil.');
    } finally {
      setBusy(false);
    }
  };

  const chooseAvatar = async () => {
    setBusy(true);
    try {
      const result = await ImagePicker.launchImageLibraryAsync({ mediaTypes: ['images'], allowsEditing: true, aspect: [1, 1], quality: 0.9 });
      if (result.canceled || !result.assets[0]) return;
      const context = ImageManipulator.manipulate(result.assets[0].uri);
      context.resize({ width: 512, height: 512 });
      const rendered = await context.renderAsync();
      const compressed = await rendered.saveAsync({ format: SaveFormat.JPEG, compress: 0.78, base64: true });
      if (!compressed.base64) throw new Error('No se pudo procesar la foto seleccionada.');
      await saveProfile({ upeerId: identity.upeerId, publicKey: identity.publicKey, alias: alias.trim().slice(0, 64), avatar: `data:image/jpeg;base64,${compressed.base64}` });
      setAvatar((await loadProfile())?.avatar);
      setError('');
      onProfileUpdated();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'No se pudo guardar la foto de perfil.');
    } finally {
      setBusy(false);
    }
  };

  const copyProfileAddress = () => { void copyToClipboard(fullId, 'Copiado al portapapeles'); };
  const copyQrAddress = () => { copyProfileAddress(); setQrOpen(false); };
  const copyProfileValue = (value: string) => { void copyToClipboard(value, 'Copiado al portapapeles'); };

  return (
    <YStack flex={1} backgroundColor={theme.background}>
      <ScrollView contentContainerStyle={styles.content}>
        <H1 color={theme.text} fontSize={scale(24)} paddingHorizontal={16}>Perfil</H1>
        <ProfileHeroSection identity={identity} alias={alias} avatar={avatar} busy={busy} onAliasChange={(value) => { setAlias(value); setError(''); }} onAliasSave={() => void saveAlias()} onAvatarPress={() => void chooseAvatar()} />
        {error ?
          <Paragraph accessibilityRole="alert" color="danger">{error}</Paragraph> : null}
        <ProfileStatsSection reputation={null} vaultStats={null} reputationUnavailable vaultUnavailable onInfo={setInfo} />
        <ProfileAddressSection fullId={fullId} onCopy={copyProfileAddress} onShowQr={() => setQrOpen(true)} onInfo={() => setInfo('direccion')} />
        <DeviceAuthorizerPanel onInfo={() => setInfo('dispositivos')} />
        <ProfileTechnicalSection identity={identity} address={address} onInfo={setInfo} onCopy={copyProfileValue} />
        <YStack paddingHorizontal={16}><NativeRippleButton backgroundColor="transparent" borderColor={theme.border} borderWidth={1} color={theme.danger} height={48} icon={<LogOut color={theme.danger} size={18} />} onPress={() => confirmSignOut(onSignOut)}>Cerrar sesión y borrar datos locales</NativeRippleButton></YStack>
      </ScrollView>
      <ProfileQrModal visible={qrOpen} fullId={fullId} onClose={() => setQrOpen(false)} onCopy={copyQrAddress} />
      <ProfileInfoModal info={info} onClose={() => setInfo(null)} />
    </YStack>
  );
}

function ProfileQrModal({ visible, fullId, onClose, onCopy }: { visible: boolean; fullId: string; onClose: () => void; onCopy: () => void }) {
  const theme = useTheme();
  const scale = useTextScale();
  return (
    <Modal animationType="fade" onRequestClose={onClose} transparent visible={visible}>
      <NativeRipplePressable android_ripple={{ color: 'transparent' }} onPress={onClose} style={[styles.modalBackdrop, { backgroundColor: theme.backdrop }]}>
        <NativeRipplePressable onPress={(event) => event.stopPropagation()} style={[styles.qrDialog, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
          <XStack alignItems="center" justifyContent="space-between" width="100%"><SizableText color={theme.text} fontSize={scale(17)} fontWeight="700">Tu dirección de contacto</SizableText><NativeRipplePressable accessibilityLabel="Cerrar" onPress={onClose}><X color={theme.placeholder} size={20} /></NativeRipplePressable></XStack>
          <Text color={theme.placeholder} fontSize={scale(12)} textAlign="center">Muestra este código para que alguien te agregue como contacto</Text>
          <View style={[styles.qrCanvas, { backgroundColor: theme.background }]}>{fullId ? <QRCode value={fullId} size={220} ecl="M" /> : <Text color={theme.placeholder}>Sin dirección</Text>}</View>
          <Text color={theme.textSecondary} fontFamily="$mono" fontSize={scale(11)} lineHeight={scale(17)}>{fullId || '—'}</Text>
          <NativeRippleButton backgroundColor={theme.accent} color={theme.onAccent} icon={<Copy color={theme.onAccent} />} onPress={onCopy} width="100%">Copiar dirección</NativeRippleButton>
        </NativeRipplePressable>
      </NativeRipplePressable>
    </Modal>
  );
}

function ProfileInfoModal({ info, onClose }: { info: ProfileInfoKey | null; onClose: () => void }) {
  return <InfoDialog content={info ? profileHelpContent[info] : null} onClose={onClose} />;
}

const styles = StyleSheet.create({ content: { flexGrow: 1, paddingBottom: 24 }, callsContent: { flexGrow: 1, gap: 12, paddingBottom: 24, paddingHorizontal: 20, paddingTop: 20 }, modalBackdrop: { alignItems: 'center', flex: 1, justifyContent: 'center', padding: 20 }, qrDialog: { alignItems: 'center', borderRadius: 16, borderWidth: 1, gap: 14, maxWidth: 380, padding: 22, width: '100%' }, qrCanvas: { alignItems: 'center', borderRadius: 12, justifyContent: 'center', minHeight: 250, padding: 15, width: 250 } });