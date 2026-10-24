import { useCallback, useEffect, useState } from 'react';
import { Alert, StyleSheet, View } from 'react-native';
import { ShieldOff } from '@tamagui/lucide-icons-2';
import { Card, Paragraph, SizableText, Text, YStack } from 'tamagui';

import { ContactTrustAvatar } from '@/components/contact-trust-avatar';
import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { useTheme } from '@/hooks/use-theme';
import { getContacts, saveContact, type UPeerContact } from '@/storage/contact-store';

export function BlockedSettingsSection() {
  const theme = useTheme();
  const [blocked, setBlocked] = useState<UPeerContact[]>([]);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    try {
      const contacts = await getContacts();
      setBlocked(contacts.filter((contact) => contact.status === 'blocked'));
      setError('');
    } catch (cause) {
      console.warn('No se pudo leer la lista de bloqueados.', cause);
      setError('No se pudo leer la lista de bloqueados');
    }
  }, []);

  useEffect(() => {
    let active = true;
    getContacts()
      .then((contacts) => {
        if (!active) return;
        setBlocked(contacts.filter((contact) => contact.status === 'blocked'));
        setError('');
      })
      .catch((cause: unknown) => {
        if (!active) return;
        console.warn('No se pudo leer la lista de bloqueados.', cause);
        setError('No se pudo leer la lista de bloqueados');
      });
    return () => { active = false; };
  }, []);

  const unblock = (contact: UPeerContact) => {
    Alert.alert('Desbloquear contacto', `¿Quieres desbloquear a ${contact.name}? Volverá a poder enviarte mensajes.`, [
      { text: 'Cancelar', style: 'cancel' },
      {
        text: 'Desbloquear',
        onPress: () => {
          saveContact({ ...contact, status: 'connected' }).then(load).catch((cause: unknown) => {
            console.warn('No se pudo desbloquear el contacto.', cause);
            setError('No se pudo desbloquear el contacto');
          });
        },
      },
    ]);
  };

  return <YStack gap={12}>
    <Card backgroundColor={theme.backgroundElement} borderColor={theme.border} borderRadius={14} borderWidth={1} gap={6} padding={16}>
      <SizableText color={theme.text} fontSize={22} fontWeight="700">Bloqueados</SizableText>
      <Paragraph color={theme.placeholder} fontSize={13}>Estos contactos no pueden enviarte mensajes nuevos. El servicio descarta sus paquetes antes de descifrarlos.</Paragraph>
    </Card>

    {error ? <Paragraph accessibilityRole="alert" color={theme.danger} fontSize={12}>{error}</Paragraph> : null}

    {blocked.length === 0 ? <YStack alignItems="center" gap={10} paddingHorizontal={24} paddingTop={32}>
      <View style={[styles.circle, { backgroundColor: theme.backgroundElement }]}><ShieldOff color={theme.placeholder} size={26} /></View>
      <SizableText color={theme.text} fontSize={17} fontWeight="600">No has bloqueado a nadie</SizableText>
      <Paragraph color={theme.placeholder} fontSize={13} textAlign="center">Puedes bloquear desde la ficha de un contacto o desde una solicitud de mensaje.</Paragraph>
    </YStack> : <YStack borderColor={theme.border} borderRadius={14} borderWidth={1} overflow="hidden">
      {blocked.map((contact) => (
        <NativeRipplePressable
          accessibilityHint="Vuelve a permitir sus mensajes"
          accessibilityLabel={`Desbloquear a ${contact.name}`}
          accessibilityRole="button"
          key={contact.upeerId}
          onPress={() => unblock(contact)}
          style={{ alignItems: 'center', borderBottomColor: theme.border, borderBottomWidth: 1, flexDirection: 'row', gap: 12, minHeight: 68, paddingHorizontal: 14, paddingVertical: 10 }}
        >
          <ContactTrustAvatar name={contact.name} size={40} />
          <YStack flex={1} gap={1}>
            <SizableText color={theme.text} fontSize={15} fontWeight="600">{contact.name}</SizableText>
            <Text color={theme.placeholder} fontFamily="monospace" fontSize={11} numberOfLines={1}>{contact.upeerId}</Text>
          </YStack>
          <Text color={theme.accent} fontSize={13} fontWeight="600">Desbloquear</Text>
        </NativeRipplePressable>
      ))}
    </YStack>}

    {blocked.length === 0 ? null : <Paragraph color={theme.placeholder} fontSize={12}>Pulsa un contacto para desbloquearlo. La conversación no reaparece hasta que esa persona vuelva a escribir.</Paragraph>}
  </YStack>;
}

const styles = StyleSheet.create({
  circle: { alignItems: 'center', borderRadius: 32, height: 64, justifyContent: 'center', width: 64 },
});
