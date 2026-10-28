import { useCallback, useEffect, useRef, useState } from 'react';

import type { ProtocolMessage } from '../../modules/upeer-transport/src/UPeerTransport.types';
import { getMessagesBefore } from '../../modules/upeer-transport/src/UPeerTransportModule';

const PAGE_SIZE = 200;

type UseChatSearchJumpOptions = {
  chatId: string;
  loaded: boolean;
  messages: readonly ProtocolMessage[];
  onJump: (messageID: string) => void;
  onMerge: (page: readonly ProtocolMessage[]) => void;
  onMissing: () => void;
};

type SearchTarget = {
  id: string;
  request: number;
  timestamp: number;
};

export function useChatSearchJump({ chatId, loaded, messages, onJump, onMerge, onMissing }: UseChatSearchJumpOptions) {
  const [target, setTarget] = useState<SearchTarget | null>(null);
  const requestRef = useRef(0);
  const jumpedRef = useRef(new Set<string>());
  const attemptedRef = useRef(new Set<string>());
  const missingRef = useRef(new Set<string>());

  const jump = useCallback((messageID: string, timestamp: number) => {
    requestRef.current += 1;
    jumpedRef.current.delete(messageID);
    setTarget({ id: messageID, request: requestRef.current, timestamp });
  }, []);

  useEffect(() => {
    if (!target || !loaded) return;
    const { id, request, timestamp } = target;
    if (messages.some((message) => message.id === id)) {
      if (jumpedRef.current.has(id)) return;
      jumpedRef.current.add(id);
      onJump(id);
      return;
    }
    if (attemptedRef.current.has(id)) {
      if (missingRef.current.has(id)) return;
      missingRef.current.add(id);
      onMissing();
      return;
    }
    attemptedRef.current.add(id);
    void (async () => {
      try {
        const page = await getMessagesBefore(chatId, timestamp + 1, PAGE_SIZE);
        if (requestRef.current !== request) return;
        if (!page.some((message) => message.id === id)) {
          missingRef.current.add(id);
          onMissing();
          return;
        }
        onMerge(page);
      } catch (cause) {
        console.warn('No se pudo cargar el historial del resultado buscado.', cause);
        if (requestRef.current !== request) return;
        missingRef.current.add(id);
        onMissing();
      }
    })();
  }, [chatId, loaded, messages, onJump, onMerge, onMissing, target]);

  return jump;
}
