import { useEffect, useState } from 'react';

import { getContacts } from '@/storage/contact-store';
import { useOwnVouchScore } from '@/hooks/use-vouch-score';

export type OwnReputation = { vouchScore: number; connectionCount: number };

export function useOwnReputation(upeerId?: string | null): OwnReputation | null {
  const vouchScore = useOwnVouchScore(upeerId);
  const [connections, setConnections] = useState<number | null>(null);

  useEffect(() => {
    getContacts()
      .then((contacts) => setConnections(contacts.filter((contact) => contact.status === 'connected' || contact.status === 'offline').length))
      .catch((cause: unknown) => {
        console.warn('No se pudieron contar los contactos directos para la reputación.', cause);
        setConnections(0);
      });
  }, []);

  return vouchScore === undefined || connections === null ? null : { vouchScore, connectionCount: connections };
}
