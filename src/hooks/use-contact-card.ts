import { useCallback, useMemo } from 'react';
import { Alert } from 'react-native';

import type { ContactCard } from '@/domain/contact-card';
import { saveContact } from '@/storage/contact-store';
import { registerPeer } from '../../modules/upeer-transport/src/UPeerTransportModule';

type ContactCardOptions = {
  contacts: readonly { upeerId: string }[];
  onOpenContact: (upeerId: string) => void;
  onSaved: () => void;
};

export function useContactCard({ contacts, onOpenContact, onSaved }: ContactCardOptions) {
  const savedIds = useMemo(() => new Set(contacts.map((contact) => contact.upeerId.toLowerCase())), [contacts]);

  const open = useCallback((card: ContactCard) => {
    const target = card.upeerId.toLowerCase();
    if (savedIds.has(target)) {
      onOpenContact(target);
      return;
    }
    const alias = card.name || target.slice(0, 12);
    const save = () => {
      saveContact({ address: card.address, createdAt: Date.now(), name: alias, publicKey: card.publicKey ?? null, status: 'pending', upeerId: target })
        .then(async () => {
          await registerPeer(target, card.address, '', '').catch((cause: unknown) => {
            console.warn('El contacto se guardó, pero el servicio nativo no lo registró.', cause);
          });
          onSaved();
          onOpenContact(target);
        })
        .catch((cause: unknown) => {
          console.warn('No se pudo guardar el contacto de la tarjeta.', cause);
          Alert.alert('No se pudo añadir', 'El contacto compartido no se pudo guardar en la agenda.');
        });
    };
    Alert.alert('Añadir contacto', `¿Quieres añadir a ${alias} a tus contactos?`, [
      { text: 'Cancelar', style: 'cancel' },
      { text: 'Añadir', onPress: save },
    ]);
  }, [onOpenContact, onSaved, savedIds]);

  return { open, savedIds };
}
