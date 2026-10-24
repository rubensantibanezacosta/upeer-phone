import { useEffect, useState } from 'react';

import { countUnreadChats } from '@/domain/chat-unread';
import { getConversations } from '../../modules/upeer-transport/src/UPeerTransportModule';

const REFRESH_MS = 3000;

export function useUnreadChats(): number {
  const [count, setCount] = useState(0);

  useEffect(() => {
    const load = () => {
      getConversations()
        .then((conversations) => setCount(countUnreadChats(conversations)))
        .catch((cause: unknown) => console.warn('No se pudo contar los chats sin leer para la pestaña.', cause));
    };
    load();
    const timer = setInterval(load, REFRESH_MS);
    return () => clearInterval(timer);
  }, []);

  return count;
}
