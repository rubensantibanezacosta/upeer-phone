import { useCallback, useEffect, useState } from 'react';
import { Alert, ScrollView, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Archive, ArrowLeft, Ban, Bell, BellOff, Images, LockKeyhole, LockOpen, Share2, Star, Trash2, UserMinus } from '@tamagui/lucide-icons-2';
import { SizableText, Text, XStack, YStack } from 'tamagui';

import { ContactInfoHero } from '@/components/contact-info/contact-info-hero';
import { ChatCipherSheet } from '@/components/chat/chat-cipher-sheet';
import { ContactShareSheet } from '@/components/chat/contact-share-sheet';
import { InteractiveRow } from '@/components/contact-info/interactive-row';
import { InfoSections, SectionSeparator, SettingsGroup } from '@/components/settings-section-parts';
import { CopyField } from '@/components/copy-field';
import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { useSnackbar } from '@/components/snackbar';
import { useCopyToClipboard } from '@/hooks/use-copy-to-clipboard';
import { useSelfUpeerId } from '@/hooks/use-self-upeer-id';
import { usePresenceLabel } from '@/hooks/use-presence-label';
import { usePeerSecurity } from '@/hooks/use-peer-security';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';
import { useConversationFlags } from '@/hooks/use-conversation-flags';
import { resolveContactName } from '@/domain/contact-name';
import { isSelfConversation } from '@/domain/self-conversation';
import { flagMessage, flagsFor, type ConversationFlag } from '@/domain/conversation-flags';
import { requestChatSearch } from '@/storage/chat-search-request';
import { setConversationFlag } from '@/storage/conversation-flags-store';
import { deleteContactAndConversation, getContacts, saveContact, setContactFavorite, type UPeerContact } from '@/storage/contact-store';
import { deleteConversation as clearNativeConversation, getConversations } from '../../../modules/upeer-transport/src/UPeerTransportModule';

type ContactInfoScreenProps = {
  upeerId: string;
  onBack: () => void;
};

const PRESENCE_POLL_MS = 5000;

export function ContactInfoScreen({ upeerId, onBack }: ContactInfoScreenProps) {
  const theme = useTheme();
  const scale = useTextScale();
  const { showSnackbar } = useSnackbar();
  const copyToClipboard = useCopyToClipboard();
  const [contact, setContact] = useState<UPeerContact | null | undefined>(undefined);
  const [presenceAt, setPresenceAt] = useState<number | undefined>(undefined);
  const [peerAvatar, setPeerAvatar] = useState<string | undefined>(undefined);
  const [peerAlias, setPeerAlias] = useState<string | undefined>(undefined);
  const [sharing, setSharing] = useState(false);
  const [cipherOpen, setCipherOpen] = useState(false);
  const cipher = usePeerSecurity();
  const loadCipher = cipher.load;
  const conversationFlags = useConversationFlags();
  const flags = flagsFor(conversationFlags, upeerId);
  const ownUpeerId = useSelfUpeerId();
  const isSelf = isSelfConversation(upeerId, ownUpeerId);

  const reportFailure = useCallback((cause: unknown) => {
    console.warn('No se pudo cargar la ficha del contacto.', cause);
    showSnackbar('No se pudo cargar la ficha del contacto');
  }, [showSnackbar]);

  const load = useCallback(() => Promise.all([getContacts(), getConversations()]).then(([list, conversations]) => {
    setContact(list.find((item) => item.upeerId === upeerId) ?? null);
    const ownConversation = conversations.find((conversation) => conversation.upeerId === upeerId);
    setPresenceAt(ownConversation?.lastActive);
    setPeerAvatar(ownConversation?.avatar);
    setPeerAlias(ownConversation?.alias);
  }), [upeerId]);

  useEffect(() => {
    load().catch(reportFailure);
    loadCipher(upeerId);
  }, [load, loadCipher, reportFailure, upeerId]);

  useEffect(() => {
    const timer = setInterval(() => {
      load().catch((cause: unknown) => console.warn('No se pudo actualizar la presencia del contacto.', cause));
    }, PRESENCE_POLL_MS);
    return () => clearInterval(timer);
  }, [load]);
  const presence = usePresenceLabel(presenceAt);

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

  const confirmClearChat = () => {
    Alert.alert('Vaciar chat', 'Se borrarán todos los mensajes de esta conversación en este dispositivo. El contacto se conserva.', [
      { text: 'Cancelar', style: 'cancel' },
      {
        text: 'Vaciar',
        style: 'destructive',
        onPress: () => {
          clearNativeConversation(upeerId)
            .then(() => showSnackbar('Conversación vaciada'))
            .catch((cause: unknown) => {
              console.warn('No se pudo vaciar la conversación.', cause);
              showSnackbar('No se pudo vaciar la conversación');
            });
        },
      },
    ]);
  };

  const confirmDelete = () => {
    if (!contact) return;
    Alert.alert('Eliminar contacto', `¿Quieres eliminar a ${contact.name}? Se borrará también la conversación y, si vuelve a escribir, aparecerá como solicitud.`, [
      { text: 'Cancelar', style: 'cancel' },
      {
        text: 'Eliminar',
        style: 'destructive',
        onPress: () => {
          deleteContactAndConversation(upeerId).then(() => {
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

  const toggleFavorite = () => {
    if (!contact) return;
    const next = contact.isFavorite !== true;
    setContactFavorite(contact.upeerId, next)
      .then(load)
      .then(() => showSnackbar(next ? 'Añadido a Favoritos' : 'Quitado de Favoritos'))
      .catch((cause: unknown) => {
        console.warn('No se pudo cambiar el favorito del contacto.', cause);
        showSnackbar('No se pudo cambiar el favorito');
      });
  };

  const toggleFlag = (flag: ConversationFlag) => {
    setConversationFlag(upeerId, flag)
      .then(() => showSnackbar(flagMessage(flag, !flags[flag])))
      .catch((cause: unknown) => {
        console.warn('No se pudo cambiar el ajuste de la conversación.', cause);
        showSnackbar('No se pudo cambiar el ajuste de la conversación');
      });
  };

  const pending = (label: string) => () => showSnackbar(`${label}: pendiente en el roadmap`);
  const openSearch = () => {
    requestChatSearch(upeerId);
    onBack();
  };
  const alias = resolveContactName(contact?.name, peerAlias, upeerId);
  const blocked = contact?.status === 'blocked';

  return (
    <SafeAreaView edges={['top', 'bottom']} style={[styles.root, { backgroundColor: theme.background }]}>
      <XStack alignItems="center" gap={4} paddingHorizontal={8} paddingVertical={8}>
        <NativeRipplePressable accessibilityLabel="Volver a la conversación" accessibilityRole="button" onPress={onBack} style={styles.headerButton}>
          <ArrowLeft color={theme.text} size={22} />
        </NativeRipplePressable>
        <SizableText color={theme.text} flex={1} fontSize={scale(17)} fontWeight="700" numberOfLines={1}>{isSelf ? 'Info. de tu cuenta' : 'Info. del contacto'}</SizableText>
      </XStack>
      <ScrollView contentContainerStyle={styles.content}>
        <ContactInfoHero alias={alias} avatar={peerAvatar} isSelf={isSelf} onCall={pending('Llamada de voz')} onSearch={openSearch} onVideo={pending('Videollamada')} presence={presence} status={contact?.status ?? 'offline'} upeerId={upeerId} />
        <YStack gap={6}>
          <Text color={theme.placeholder} fontSize={scale(12)} fontWeight="600">Identidad uPeer</Text>
          <CopyField accessibilityLabel="Copiar identidad uPeer" onCopy={() => { void copyToClipboard(upeerId, 'Identidad copiada'); }} truncate value={upeerId} />
          {contact?.address ? <CopyField accessibilityLabel="Copiar dirección de red" onCopy={() => { void copyToClipboard(contact.address, 'Dirección copiada'); }} truncate value={contact.address} /> : null}
        </YStack>
        <InfoSections>
          <SectionSeparator />
          <InteractiveRow chevron icon={Images} label="Multimedia" onPress={pending('Multimedia')} />
          <SectionSeparator />
          <SettingsGroup>
            {isSelf ? null : <InteractiveRow disabled={!contact} icon={Share2} label="Compartir contacto" onPress={() => setSharing(true)} />}
            <InteractiveRow icon={flags.muted ? Bell : BellOff} label={flags.muted ? 'Reactivar notificaciones' : 'Silenciar notificaciones'} onPress={() => toggleFlag('muted')} />
            <InteractiveRow icon={Archive} label="Archivar chat" onPress={() => toggleFlag('archived')} />
            {isSelf ? null : <InteractiveRow icon={Star} label={contact?.isFavorite ? 'Quitar de Favoritos' : 'Añadir a Favoritos'} onPress={toggleFavorite} />}
            <InteractiveRow chevron icon={Star} label="Mensajes destacados" onPress={pending('Mensajes destacados')} />
            <InteractiveRow chevron icon={LockKeyhole} label="Cifrado" onPress={() => setCipherOpen(true)} />
          </SettingsGroup>
          <SectionSeparator />
          <SettingsGroup>
            <InteractiveRow icon={Trash2} label="Vaciar chat" onPress={confirmClearChat} />
            {isSelf ? null : (
              <>
                <InteractiveRow disabled={!contact} icon={blocked ? LockOpen : Ban} label={blocked ? 'Desbloquear' : `Bloquear a ${alias}`} onPress={toggleBlock} tone="danger" />
                <InteractiveRow disabled={!contact} icon={UserMinus} label="Eliminar contacto" onPress={confirmDelete} tone="danger" />
              </>
            )}
          </SettingsGroup>
        </InfoSections>
      </ScrollView>
      <ContactShareSheet
        contact={contact ?? null}
        onClose={() => setSharing(false)}
        onError={showSnackbar}
        onSent={(target) => showSnackbar(`Contacto compartido con ${target.name}`)}
        visible={sharing}
      />
      <ChatCipherSheet alias={alias} error={cipher.state.error} loading={cipher.state.loading} onClose={() => setCipherOpen(false)} security={cipher.state.security} visible={cipherOpen} />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  headerButton: { alignItems: 'center', borderRadius: 22, height: 44, justifyContent: 'center', width: 44 },
  content: { gap: 18, paddingBottom: 32, paddingHorizontal: 20, paddingTop: 4 },
});
