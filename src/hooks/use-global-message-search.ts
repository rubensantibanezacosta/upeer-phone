import { useEffect, useState } from 'react';

import { normalizeSearchText, searchMessageText } from '@/domain/global-search';
import { getMessages } from '../../modules/upeer-transport/src/UPeerTransportModule';

export type MessageSearchHit = {
  id: string;
  chatUpeerId: string;
  content: string;
  timestamp: number;
  isMine: boolean;
};

const DEBOUNCE_MS = 300;
const MIN_QUERY_LENGTH = 2;
const PER_CHAT_LIMIT = 200;
const TOTAL_LIMIT = 200;

export function useGlobalMessageSearch(query: string, chatIds: readonly string[]): MessageSearchHit[] {
  const [hits, setHits] = useState<MessageSearchHit[]>([]);
  const key = chatIds.join('|');

  useEffect(() => {
    const needle = normalizeSearchText(query.trim());
    let cancelled = false;
    const timer = setTimeout(() => {
      if (needle.length < MIN_QUERY_LENGTH) {
        setHits([]);
        return;
      }
      const run = async () => {
        const found: MessageSearchHit[] = [];
        for (const chatUpeerId of key.split('|')) {
          if (cancelled || found.length >= TOTAL_LIMIT) break;
          if (!chatUpeerId) continue;
          try {
            const messages = await getMessages(chatUpeerId, PER_CHAT_LIMIT);
            for (const message of messages) {
              if (cancelled || found.length >= TOTAL_LIMIT) break;
              const text = searchMessageText(message.content);
              if (!text || !normalizeSearchText(text).includes(needle)) continue;
              found.push({ id: message.id, chatUpeerId, content: message.content, timestamp: message.timestamp, isMine: message.isMine });
            }
          } catch (cause) {
            console.warn('No se pudieron leer los mensajes para la búsqueda global.', cause);
          }
        }
        if (!cancelled) setHits(found);
      };
      void run();
    }, DEBOUNCE_MS);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [key, query]);

  return hits;
}
