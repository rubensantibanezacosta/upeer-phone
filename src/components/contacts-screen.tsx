import { useCallback, useEffect, useState } from 'react';
import { Alert, ScrollView } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Card, H1, Input, Paragraph, SizableText, Text, XStack, YStack } from 'tamagui';
import type { UPeerIdentity } from '@/domain/identity';
import { NativeRippleButton } from '@/components/native-ripple-button';
import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { deleteContact, getContacts, saveContact, type UPeerContact } from '@/storage/contact-store';
import { loadProfile, saveProfile } from '@/storage/identity-store';

type ContactsScreenProps = { identity: UPeerIdentity; onSignOut: () => void; onContactsChange?: (contacts: UPeerContact[]) => void };

export function ContactsScreen({ identity, onSignOut, onContactsChange }: ContactsScreenProps) {
  const [contacts, setContacts] = useState<UPeerContact[]>([]);
  const [contactInput, setContactInput] = useState('');
  const [alias, setAlias] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const refresh = useCallback(async () => {
    const nextContacts = await getContacts();
    setContacts(nextContacts);
    onContactsChange?.(nextContacts);
  }, [onContactsChange]);

  useEffect(() => {
    void (async () => {
      try {
        await refresh();
        const profile = await loadProfile();
        setAlias(profile?.alias ?? '');
      } catch (cause) {
        setError(cause instanceof Error ? cause.message : 'No se pudieron cargar los contactos y el perfil.');
      }
    })();
  }, [refresh]);

  const addContact = async () => {
    const separator = contactInput.indexOf('@');
    const upeerId = contactInput.slice(0, separator).trim();
    const address = contactInput.slice(separator + 1).trim();
    if (separator < 1 || !/^[0-9a-f]{64}$/i.test(upeerId) || !/^([23][0-9a-f]{2}:)/i.test(address)) {
      setError('Formato esperado: uPeerID de 64 caracteres hexadecimales@dirección IPv6 200::/7 o 300::/8.');
      return;
    }
    if (upeerId.toLowerCase() === identity.upeerId) {
      setError('No puedes añadirte como contacto.');
      return;
    }
    try {
      await saveContact({ upeerId: upeerId.toLowerCase(), address, name: upeerId.slice(0, 12), publicKey: null, status: 'pending', createdAt: Date.now() });
      await refresh();
      setContactInput('');
      Alert.alert('Contacto guardado', 'El contacto se guardó en este dispositivo.');
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'No se pudo guardar el contacto.');
    }
  };

  const saveAlias = async () => {
    await saveProfile({ upeerId: identity.upeerId, publicKey: identity.publicKey, alias: alias.trim().slice(0, 64) });
  };

  const removeContact = async (upeerId: string) => {
    await deleteContact(upeerId);
    await refresh();
  };

  const lockIdentity = async () => {
    setBusy(true);
    setError('');
    try {
      onSignOut();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'No se pudieron borrar los datos de la cuenta.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: '#202124' }}>
      <ScrollView contentContainerStyle={{ alignSelf: 'center', gap: 18, paddingVertical: 20, paddingBottom: 36, width: '100%', maxWidth: 640 }}>
        <YStack gap={8} paddingVertical={12}>
     
          <H1 color="color" fontSize={24} fontWeight="700">Contactos</H1>
          <Paragraph color="placeholder-color">Gestiona tu identidad y mantén tu red privada conectada.</Paragraph>
        </YStack>
        <Card borderWidth={1} backgroundColor="color-1" borderColor="border-color" borderRadius={18} padding={18}>
          <YStack gap={12}>
            <SizableText color="color" fontSize={17} fontWeight="700">Tu perfil</SizableText>
            <Input accessibilityLabel="Nombre visible" autoCapitalize="words" backgroundColor="color-2" borderColor="border-color" color="color" onBlur={() => { void saveAlias(); }} onChangeText={setAlias} placeholder="Tu nombre" placeholderTextColor="placeholder-color" value={alias} />
            <Text color="placeholder-color" style={{ fontFamily: 'monospace', userSelect: 'text' }} fontSize={12}>{identity.upeerId}</Text>
          </YStack>
        </Card>
        <Card borderWidth={1} backgroundColor="color-1" borderColor="border-color" borderRadius={18} padding={18}>
          <YStack gap={12}>
            <SizableText color="color" fontSize={17} fontWeight="700">Añadir contacto</SizableText>
            <Input accessibilityLabel="uPeerID y dirección Yggdrasil del contacto" autoCapitalize="none" autoCorrect={false} backgroundColor="color-2" borderColor="border-color" color="color" onChangeText={setContactInput} placeholder="uPeerID@200::…" placeholderTextColor="placeholder-color" value={contactInput} />
            <NativeRippleButton backgroundColor="accent-background" color="accent-foreground" fontWeight="700" height={48} onPress={() => void addContact()}>Guardar contacto</NativeRippleButton>
          </YStack>
        </Card>
        {!!error && <Paragraph accessibilityRole="alert" color="danger">{error}</Paragraph>}
        <YStack gap={12}>
          <SizableText color="color" fontSize={18} fontWeight="700">Tus contactos <SizableText color="placeholder-color" fontSize={15}>({contacts.length})</SizableText></SizableText>
          {contacts.length === 0 ? <Card borderWidth={1} backgroundColor="color-1" borderColor="border-color" borderRadius={18} padding={18}><Paragraph color="placeholder-color">Aún no tienes contactos. Añade uno para empezar.</Paragraph></Card> : contacts.map((contact) => (
            <Card key={contact.upeerId} borderWidth={1} backgroundColor="color-1" borderColor="border-color" borderRadius={14} padding={16}>
              <XStack alignItems="center" gap={12}>
                <YStack alignItems="center" backgroundColor="color-3" borderRadius={99} height={42} justifyContent="center" width={42}><SizableText color="accent-color" fontSize={17} fontWeight="700">{contact.name.charAt(0).toUpperCase()}</SizableText></YStack>
                <YStack flex={1} gap={4}>
                  <SizableText color="color" fontSize={15} fontWeight="700">{contact.name}</SizableText>
                  <Text color="placeholder-color" style={{ fontFamily: 'monospace' }} fontSize={11}>{contact.upeerId.slice(0, 20)}…</Text>
                  <Paragraph color="placeholder-color" fontSize={12}>{contact.status}</Paragraph>
                </YStack>
                <NativeRipplePressable accessibilityRole="button" onPress={() => void removeContact(contact.upeerId)}><SizableText color="danger" fontSize={13} fontWeight="600">Eliminar</SizableText></NativeRipplePressable>
              </XStack>
            </Card>
          ))}
        </YStack>
        <NativeRippleButton backgroundColor="transparent" borderColor="border-color" borderWidth={1} color="danger" disabled={busy} height={48} onPress={() => void lockIdentity()}>Cerrar sesión y borrar datos locales</NativeRippleButton>
      </ScrollView>
    </SafeAreaView>
  );
}

