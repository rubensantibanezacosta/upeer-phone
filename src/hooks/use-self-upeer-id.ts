import { useEffect, useState } from 'react';

import { loadProfile } from '@/storage/identity-store';

export function useSelfUpeerId(): string {
  const [selfUpeerId, setSelfUpeerId] = useState('');

  useEffect(() => {
    let active = true;
    void loadProfile()
      .then((profile) => {
        if (active && profile) setSelfUpeerId(profile.upeerId.toLowerCase());
      })
      .catch((cause: unknown) => console.warn('No se pudo cargar el perfil propio.', cause));
    return () => {
      active = false;
    };
  }, []);

  return selfUpeerId;
}
