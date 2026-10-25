import { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, ScrollView, StyleSheet } from 'react-native';
import { router } from 'expo-router';
import { SafeAreaView } from 'react-native-safe-area-context';
import { UserPlus, Users } from '@tamagui/lucide-icons-2';
import { H1, Paragraph, SizableText, Text, XStack, YStack } from 'tamagui';
import type { UPeerIdentity } from '@/domain/identity-record';
import { BottomSheet } from '@/components/bottom-sheet';
import { ContactFormSheet, type ContactDraft } from '@/components/contact-form-sheet';
import { NativeRippleButton } from '@/components/native-ripple-button';
import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { ContactTrustAvatar } from '@/components/contact-trust-avatar';
import { SearchField } from '@/components/search-field';
import { resolveContactName } from '@/domain/contact-name';
import { getConversations, registerPeer as registerNativePeer } from '../../modules/upeer-transport/src/UPeerTransportModule';
import { deleteContactAndConversation, getContacts, saveContact, type UPeerContact } from '@/storage/contact-store';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

const CONTACTS_REFRESH_MS = 3000;

type ContactsScreenProps = { identity: UPeerIdentity };

const statusLabels: Record<UPeerContact['status'], string> = {
  pending: 'Pendiente',
  incoming: 'Solicitud recibida',
  connected: 'Conectado',
  offline: 'Desconectado',
  blocked: 'Bloqueado',
};

export function ContactsScreen({ identity }: ContactsScreenProps) {
  const theme = useTheme();
  const scale = useTextScale();
  const [contacts, setContacts] = useState<UPeerContact[]>([]);
  const [avatars, setAvatars] = useState<Record<string, string>>({});
  const [aliases, setAliases] = useState<Record<string, string>>({});
  const [query, setQuery] = useState('');
  const [adding, setAdding] = useState(false);
  const [error, setError] = useState('');

  const displayName = useCallback((contact: UPeerContact) => resolveContactName(contact.name, aliases[contact.upeerId], contact.upeerId), [aliases]);

  const loadContacts = useCallback(() => Promise.all([getContacts(), getConversations()]).then(([list, conversations]) => {
    setContacts(list);
    setAvatars(Object.fromEntries(conversations.filter((conversation) => conversation.avatar).map((conversation) => [conversation.upeerId, conversation.avatar as string])));
    setAliases(Object.fromEntries(conversations.filter((conversation) => conversation.alias).map((conversation) => [conversation.upeerId, conversation.alias])));
  }), []);

  const reportLoadFailure = useCallback((cause: unknown) => {
    console.warn('No se pudieron cargar los contactos.', cause);
    setError(cause instanceof Error ? cause.message : 'No se pudieron cargar los contactos.');
  }, []);

  useEffect(() => {
    loadContacts().catch(reportLoadFailure);
    const timer = setInterval(() => {
      loadContacts().catch((cause: unknown) => console.warn('No se pudieron refrescar los contactos.', cause));
    }, CONTACTS_REFRESH_MS);
    return () => clearInterval(timer);
  }, [loadContacts, reportLoadFailure]);

  const filtered = useMemo(() => {
    const needle = query.trim().toLowerCase();
    if (!needle) return contacts;
    return contacts.filter((contact) => contact.name.toLowerCase().includes(needle) || contact.upeerId.toLowerCase().includes(needle));
  }, [contacts, query]);

  const addContact = async (draft: ContactDraft) => {
    if (draft.upeerId === identity.upeerId.toLowerCase()) throw new Error('No puedes añadirte como contacto.');
    await saveContact({ upeerId: draft.upeerId, address: draft.address, name: draft.name, publicKey: null, status: 'pending', createdAt: Date.now() });
    try {
      await registerNativePeer(draft.upeerId, draft.address, '', '');
    } catch (cause) {
      console.warn('El contacto se guardó, pero el servicio nativo no lo registró.', cause);
    }
    await loadContacts();
  };

  const confirmRemove = (contact: UPeerContact) => {
    Alert.alert('Eliminar contacto', `¿Quieres eliminar a ${contact.name}? Se borrará también la conversación y, si vuelve a escribir, aparecerá como solicitud.`, [
      { text: 'Cancelar', style: 'cancel' },
      { text: 'Eliminar', style: 'destructive', onPress: () => { deleteContactAndConversation(contact.upeerId).then(loadContacts).catch(reportLoadFailure); } },
    ]);
  };

  const openChat = (contact: UPeerContact) => router.push({ pathname: '/chat/[upeerId]', params: { upeerId: contact.upeerId } });

  return (
    <SafeAreaView edges={['top']} style={[styles.root, { backgroundColor: theme.background }]}>
      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
        <XStack alignItems="center" justifyContent="space-between">
          <H1 color={theme.text} fontSize={scale(24)} fontWeight="700">Contactos</H1>
          <NativeRipplePressable accessibilityLabel="Añadir contacto" accessibilityRole="button" onPress={() => setAdding(true)} style={styles.headerButton}><UserPlus color={theme.text} size={21} /></NativeRipplePressable>
        </XStack>
        <SearchField onChangeText={setQuery} placeholder="Buscar contactos" value={query} />
        {error ? <Paragraph accessibilityRole="alert" color={theme.danger} fontSize={scale(12)}>{error}</Paragraph> : null}
        {filtered.length === 0
          ? <YStack alignItems="center" flex={1} gap={12} justifyContent="center" padding={24}>
            <YStack alignItems="center" backgroundColor={theme.backgroundElement} borderRadius={32} height={64} justifyContent="center" width={64}>
              <Users color={theme.placeholder} size={28} /></YStack>
            <SizableText color={theme.text} fontSize={scale(17)} fontWeight="600">{contacts.length === 0 ? 'Aún no hay contactos' : 'Sin resultados'}</SizableText>
            <Paragraph color={theme.placeholder} textAlign="center">{contacts.length === 0 ? 'Añade una identidad para iniciar una conversación cifrada.' : 'Prueba con otro nombre o identidad.'}</Paragraph>
            {contacts.length === 0 ? <NativeRippleButton onPress={() => setAdding(true)}>Añadir contacto</NativeRippleButton> : null}
          </YStack>
          : <YStack marginTop={2}>{filtered.map((contact) => (
            <NativeRipplePressable accessibilityHint="Mantén pulsado para eliminar el contacto" accessibilityLabel={`Abrir conversación con ${displayName(contact)}`} accessibilityRole="button" delayLongPress={350} key={contact.upeerId} onLongPress={() => confirmRemove(contact)} onPress={() => openChat(contact)} style={[styles.item, { borderBottomColor: theme.border }]}>
              <XStack alignItems="center" gap={12} minHeight={80} paddingVertical={10}>
                <ContactTrustAvatar avatar={avatars[contact.upeerId]} name={displayName(contact)} size={55} />
                <YStack flex={1} gap={4}>
                  <XStack alignItems="center" gap={8}>
                    <SizableText color={theme.text} flex={1} fontSize={scale(15)} fontWeight="600" numberOfLines={1}>{displayName(contact)}</SizableText>
                    <Text color={theme.placeholder} fontSize={scale(11)} fontWeight="500" numberOfLines={1}>{statusLabels[contact.status]}</Text>
                  </XStack>
                  <Text color={theme.placeholder} fontFamily="monospace" fontSize={scale(13)} numberOfLines={1}>{contact.upeerId}</Text>
                </YStack>
              </XStack>
            </NativeRipplePressable>
          ))}</YStack>}
      </ScrollView>
      <BottomSheet icon={<UserPlus color={theme.accent} size={20} />} onClose={() => setAdding(false)} title="Añadir contacto" visible={adding}>
        <ContactFormSheet onAdd={addContact} onDone={() => setAdding(false)} />
      </BottomSheet>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  item: { borderBottomWidth: StyleSheet.hairlineWidth, marginHorizontal: -12, overflow: 'hidden', paddingHorizontal: 12 },
  content: { flexGrow: 1, gap: 12, paddingBottom: 24, paddingHorizontal: 20, paddingTop: 20 },
  headerButton: { alignItems: 'center', borderRadius: 22, height: 44, justifyContent: 'center', overflow: 'hidden', width: 44 },
});
