import { useEffect, useState } from 'react';

import { collectSharedMedia, type SharedMediaItem } from '@/domain/shared-media';
import { getMessages } from '../../modules/upeer-transport/src/UPeerTransportModule';

const MESSAGE_PAGE = 200;
const PREVIEW_LIMIT = 5;

export function useSharedMedia({ chatId, refreshKey = 0 }: { chatId: string; refreshKey?: number }): SharedMediaItem[] {
  const [items, setItems] = useState<SharedMediaItem[]>([]);

  useEffect(() => {
    let cancelled = false;
    if (!chatId) {
      Promise.resolve().then(() => {
        if (!cancelled) setItems([]);
      });
      return () => {
        cancelled = true;
      };
    }
    getMessages(chatId, MESSAGE_PAGE)
      .then((page) => {
        if (cancelled) return;
        setItems(collectSharedMedia(page.map((message) => ({ id: message.id, timestamp: message.timestamp, content: message.content })), PREVIEW_LIMIT));
      })
      .catch((cause: unknown) => {
        console.warn('No se pudo cargar la multimedia compartida.', cause);
      });
    return () => {
      cancelled = true;
    };
  }, [chatId, refreshKey]);

  return items;
}
