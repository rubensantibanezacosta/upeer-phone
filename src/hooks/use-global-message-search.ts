import { useEffect, useState } from 'react';

import { normalizeSearchText, searchMessageText } from '@/domain/global-search';
import { searchMessages } from '../../modules/upeer-transport/src/UPeerTransportModule';

export type MessageSearchHit = {
  id: string;
  chatUpeerId: string;
  content: string;
  timestamp: number;
  isMine: boolean;
};

const DEBOUNCE_MS = 300;
const MIN_QUERY_LENGTH = 2;
const RESULT_LIMIT = 200;

export function useGlobalMessageSearch(query: string): MessageSearchHit[] {
  const [hits, setHits] = useState<MessageSearchHit[]>([]);

  useEffect(() => {
    const needle = normalizeSearchText(query.trim());
    let cancelled = false;
    const timer = setTimeout(() => {
      if (needle.length < MIN_QUERY_LENGTH) {
        setHits([]);
        return;
      }
      const run = async () => {
        try {
          const found = await searchMessages(query.trim(), RESULT_LIMIT);
          if (cancelled) return;
          setHits(found.filter((hit) => normalizeSearchText(searchMessageText(hit.content) ?? '').includes(needle)));
        } catch (cause) {
          console.warn('No se pudieron buscar los mensajes de la cuenta.', cause);
          if (!cancelled) setHits([]);
        }
      };
      void run();
    }, DEBOUNCE_MS);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [query]);

  return hits;
}
