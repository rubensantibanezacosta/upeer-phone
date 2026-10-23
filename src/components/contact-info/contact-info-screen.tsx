import { useCallback, useEffect, useState } from 'react';
import { Alert, ScrollView, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Archive, ArrowLeft, Ban, BellOff, LockKeyhole, LockOpen, Share2, Star, Trash2, UserMinus } from '@tamagui/lucide-icons-2';
import { SizableText, Text, XStack, YStack } from 'tamagui';

import { ContactInfoHero } from '@/components/contact-info/contact-info-hero';
import { InteractiveRow } from '@/components/contact-info/interactive-row';
import { CopyField } from '@/components/copy-field';
import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { useSnackbar } from '@/components/snackbar';
import { useCopyToClipboard } from '@/hooks/use-copy-to-clipboard';
import { useTheme } from '@/hooks/use-theme';
import { deleteContact, getContacts, saveContact, type UPeerContact } from '@/storage/contact-store';

type ContactInfoScreenProps = {
  upeerId: string;
  onBack: () => void;
};

export function ContactInfoScreen({ upeerId, onBack }: ContactInfoScreenProps) {
  const theme = useTheme();
  const { showSnackbar } = useSnackbar();
  const copyToClipboard = useCopyToClipboard();
  const [contact, setContact] = useState<UPeerContact | null | undefined>(undefined);

  const reportFailure = useCallback((cause: unknown) => {
    console.warn('No se pudo cargar la ficha del contacto.', cause);
    showSnackbar('No se pudo cargar la ficha del contacto');
  }, [showSnackbar]);

  const load = useCallback(() => getContacts().then((list) => setContact(list.find((item) => item.upeerId === upeerId) ?? null)), [upeerId]);

  useEffect(() => {
    load().catch(reportFailure);
  }, [load, reportFailure]);

  const toggleBlock = () => {
    if (!contact) return;
    const blocked = contact.status === 'blocked';
    const apply = () => {
      saveContact({ ...contact, status: blocked ? 'offline' : 'blocked' })
        .then(load)
        .then(() => showSnackbar(blocked ? 'Contacto desbloqueado' : 'Contacto bloqueado'))
        .catch((cause: unknown) => {
          console.warn('No se pudo cambiar el bloqueo del contacto.', cause);
          showSnackbar('No se pudo cambiar el bloqueo');
        });
    };
    if (blocked) {
      apply();
      return;
    }
    Alert.alert('Bloquear contacto', `¿Quieres bloquear a ${contact.name}? No podrá enviarte mensajes nuevos.`, [
      { text: 'Cancelar', style: 'cancel' },
      { text: 'Bloquear', style: 'destructive', onPress: apply },
    ]);
  };

  const confirmDelete = () => {
    if (!contact) return;
    Alert.alert('Eliminar contacto', `¿Quieres eliminar a ${contact.name} de tus contactos? La conversación se conserva.`, [
      { text: 'Cancelar', style: 'cancel' },
      {
        text: 'Eliminar',
        style: 'destructive',
        onPress: () => {
          deleteContact(upeerId).then(() => {
            showSnackbar('Contacto eliminado');
            onBack();
          }).catch((cause: unknown) => {
            console.warn('No se pudo eliminar el contacto.', cause);
            showSnackbar('No se pudo eliminar el contacto');
          });
        },
      },
    ]);
  };

  const pending = (label: string) => () => showSnackbar(`${label}: pendiente en el roadmap`);
  const alias = contact?.name || upeerId.slice(0, 12);
  const blocked = contact?.status === 'blocked';

  return (
    <SafeAreaView edges={['top', 'bottom']} style={[styles.root, { backgroundColor: theme.background }]}>
      <XStack alignItems="center" gap={4} paddingHorizontal={8} paddingVertical={8}>
        <NativeRipplePressable accessibilityLabel="Volver a la conversación" accessibilityRole="button" onPress={onBack} style={styles.headerButton}>
          <ArrowLeft color={theme.text} size={22} />
        </NativeRipplePressable>
        <SizableText color={theme.text} flex={1} fontSize={17} fontWeight="700" numberOfLines={1}>Info. del contacto</SizableText>
      </XStack>
      <ScrollView contentContainerStyle={styles.content}>
        <ContactInfoHero alias={alias} onCall={pending('Llamada de voz')} onSearch={pending('Buscar en la conversación')} onVideo={pending('Videollamada')} status={contact?.status ?? 'offline'} upeerId={upeerId} vouchScore={undefined} />
        <YStack gap={6}>
          <Text color={theme.placeholder} fontSize={12} fontWeight="600">Identidad uPeer</Text>
          <CopyField accessibilityLabel="Copiar identidad uPeer" onCopy={() => { void copyToClipboard(upeerId, 'Identidad copiada'); }} truncate value={upeerId} />
          {contact ? <CopyField accessibilityLabel="Copiar dirección de red" onCopy={() => { void copyToClipboard(contact.address, 'Dirección copiada'); }} truncate value={contact.address} /> : null}
        </YStack>
        <YStack gap={6}>
          <Text color={theme.placeholder} fontSize={12} fontWeight="600">Conversación</Text>
          <InteractiveRow disabled hint="Pronto" icon={Share2} label="Compartir contacto" onPress={pending('Compartir contacto')} />
          <InteractiveRow disabled hint="Pronto" icon={BellOff} label="Silenciar notificaciones" onPress={pending('Silenciar notificaciones')} />
          <InteractiveRow disabled hint="Pronto" icon={Archive} label="Archivar chat" onPress={pending('Archivar chat')} />
          <InteractiveRow disabled hint="Pronto" icon={Star} label="Añadir a Favoritos" onPress={pending('Favoritos')} />
          <InteractiveRow disabled hint="Pronto" icon={LockKeyhole} label="Cifrado" onPress={pending('Detalles de cifrado')} />
        </YStack>
        <YStack gap={6}>
          <Text color={theme.placeholder} fontSize={12} fontWeight="600">Zona de riesgo</Text>
          <InteractiveRow disabled hint="Pronto" icon={Trash2} label="Vaciar chat" onPress={pending('Vaciar chat')} />
          <InteractiveRow disabled={!contact} icon={blocked ? LockOpen : Ban} label={blocked ? 'Desbloquear contacto' : `Bloquear a ${alias}`} onPress={toggleBlock} tone="danger" />
          <InteractiveRow disabled={!contact} icon={UserMinus} label="Eliminar contacto" onPress={confirmDelete} tone="danger" />
        </YStack>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  headerButton: { alignItems: 'center', borderRadius: 22, height: 44, justifyContent: 'center', width: 44 },
  content: { gap: 18, paddingBottom: 32, paddingHorizontal: 20, paddingTop: 4 },
});
