import { useCallback, useEffect, useState } from 'react';

import { deleteMessage, sendReaction } from '../../modules/upeer-transport/src/UPeerTransportModule';
import { isReactedByMe } from '@/domain/chat-reactions';
import { loadProfile } from '@/storage/identity-store';
import type { ProtocolMessage } from '../../modules/upeer-transport/src/UPeerTransport.types';

type MessageActionsOptions = {
  onChanged: () => Promise<void>;
  onError: (message: string) => void;
};

export function useMessageActions(upeerId: string | undefined, { onChanged, onError }: MessageActionsOptions) {
  const [selected, setSelected] = useState<ProtocolMessage | null>(null);
  const [selfUpeerId, setSelfUpeerId] = useState('');

  useEffect(() => {
    let active = true;
    void loadProfile()
      .then((profile) => {
        if (active && profile) setSelfUpeerId(profile.upeerId);
      })
      .catch((cause: unknown) => console.warn('No se pudo cargar el perfil propio.', cause));
    return () => {
      active = false;
    };
  }, []);

  const open = useCallback((message: ProtocolMessage) => setSelected(message), []);
  const close = useCallback(() => setSelected(null), []);

  const toggleReaction = useCallback((message: ProtocolMessage, emoji: string) => {
    if (!upeerId) return;
    const remove = isReactedByMe(message.reactions, emoji, selfUpeerId);
    setSelected(null);
    sendReaction(upeerId, message.id, emoji, remove)
      .then(() => onChanged())
      .catch((cause: unknown) => {
        onError(cause instanceof Error ? cause.message : 'No se pudo enviar la reacción.');
      });
  }, [onChanged, onError, selfUpeerId, upeerId]);

  const remove = useCallback((message: ProtocolMessage) => {
    if (!upeerId) return;
    setSelected(null);
    deleteMessage(upeerId, message.id)
      .then(() => onChanged())
      .catch((cause: unknown) => {
        onError(cause instanceof Error ? cause.message : 'No se pudo eliminar el mensaje.');
      });
  }, [onChanged, onError, upeerId]);

  return { close, open, remove, selected, selfUpeerId, toggleReaction };
}
