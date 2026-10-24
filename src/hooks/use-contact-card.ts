import { useCallback, useEffect, useMemo, useState } from 'react';

import type { ContactCard } from '@/domain/contact-card';
import { saveContact } from '@/storage/contact-store';
import { loadProfile } from '@/storage/identity-store';
import { registerPeer } from '../../modules/upeer-transport/src/UPeerTransportModule';
import type { ContactCardAction } from '@/components/chat/contact-card-sheet';

type ContactLike = { upeerId: string; status: string };

type ContactCardOptions = {
  contacts: readonly ContactLike[];
  onOpenContact: (upeerId: string) => void;
  onSaved: () => void;
};

export function useContactCard({ contacts, onOpenContact, onSaved }: ContactCardOptions) {
  const [selected, setSelected] = useState<ContactCard | null>(null);
  const [selfUpeerId, setSelfUpeerId] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    void loadProfile()
      .then((profile) => {
        if (active && profile) setSelfUpeerId(profile.upeerId.toLowerCase());
      })
      .catch((cause: unknown) => console.warn('No se pudo cargar el perfil para la tarjeta.', cause));
    return () => {
      active = false;
    };
  }, []);

  const savedIds = useMemo(() => new Set(contacts.map((contact) => contact.upeerId.toLowerCase())), [contacts]);

  const open = useCallback((card: ContactCard) => {
    setError('');
    setBusy(false);
    setSelected(card);
  }, []);

  const close = useCallback(() => {
    setSelected(null);
    setError('');
  }, []);

  const target = selected?.upeerId.toLowerCase() ?? '';
  const existing = contacts.find((contact) => contact.upeerId.toLowerCase() === target);
  const alias = selected ? selected.name || target.slice(0, 12) : '';

  const action = useMemo<ContactCardAction>(() => {
    if (!selected) return { disabled: true, label: 'Guardar contacto', onPress: () => undefined };
    if (target === selfUpeerId) return { disabled: true, label: 'Eres tú', onPress: () => undefined };
    if (existing?.status === 'blocked') return { disabled: true, label: 'Contacto bloqueado', onPress: () => undefined };
    if (existing?.status === 'pending' || existing?.status === 'incoming') {
      return { disabled: true, label: 'Solicitud pendiente', onPress: () => undefined };
    }
    if (existing) {
      return {
        disabled: false,
        label: 'Abrir ficha del contacto',
        onPress: () => {
          close();
          onOpenContact(target);
        },
      };
    }
    const save = () => {
      setBusy(true);
      setError('');
      saveContact({ address: selected.address, createdAt: Date.now(), name: alias, publicKey: selected.publicKey ?? null, status: 'pending', upeerId: target })
        .then(async () => {
          await registerPeer(target, selected.address, '', '').catch((cause: unknown) => {
            console.warn('El contacto se guardó, pero el servicio nativo no lo registró.', cause);
          });
          onSaved();
          close();
          onOpenContact(target);
        })
        .catch((cause: unknown) => {
          console.warn('No se pudo guardar el contacto de la tarjeta.', cause);
          setError('No se pudo guardar el contacto en la agenda.');
        })
        .finally(() => setBusy(false));
    };
    return { busy, disabled: busy, label: busy ? 'Guardando…' : 'Guardar contacto', onPress: save };
  }, [alias, busy, close, existing, onOpenContact, onSaved, selected, selfUpeerId, target]);

  return { action, close, error, open, savedIds, selected };
}
