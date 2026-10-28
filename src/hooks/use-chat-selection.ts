import { useCallback, useMemo, useState } from 'react';

import { useCopyToClipboard } from '@/hooks/use-copy-to-clipboard';
import { deletableIds, forwardableMessages, orderedSelection, selectionExcerpt } from '@/domain/chat-selection';
import { describeMessageContent } from '@/domain/attachment';
import type { ProtocolMessage } from '../../modules/upeer-transport/src/UPeerTransport.types';
import { deleteMessage } from '../../modules/upeer-transport/src/UPeerTransportModule';

type ChatSelectionOptions = {
  messages: readonly ProtocolMessage[];
  onChanged: () => Promise<void>;
  onError: (message: string) => void;
  onForward: (messages: ProtocolMessage[]) => void;
  upeerId: string | undefined;
};

export function useChatSelection({ messages, onChanged, onError, onForward, upeerId }: ChatSelectionOptions) {
  const copy = useCopyToClipboard();
  const [active, setActive] = useState(false);
  const [selected, setSelected] = useState<ReadonlySet<string>>(new Set());

  const clear = useCallback(() => {
    setActive(false);
    setSelected(new Set());
  }, []);

  const start = useCallback((first?: ProtocolMessage) => {
    setActive(true);
    setSelected(first ? new Set([first.id]) : new Set());
  }, []);

  const toggle = useCallback((message: ProtocolMessage) => {
    setSelected((current) => {
      const next = new Set(current);
      if (next.has(message.id)) next.delete(message.id);
      else next.add(message.id);
      return next;
    });
  }, []);

  const chosen = useMemo(() => orderedSelection(messages, selected), [messages, selected]);
  const deletable = useMemo(() => deletableIds(messages, selected), [messages, selected]);
  const forwardable = useMemo(() => forwardableMessages(messages, selected), [messages, selected]);

  const copySelected = useCallback(() => {
    const text = selectionExcerpt(chosen.filter((message) => message.deleted !== true).map((message) => describeMessageContent(message.content)));
    if (!text) return;
    void copy(text, chosen.length === 1 ? 'Mensaje copiado' : `${chosen.length} mensajes copiados`);
    clear();
  }, [chosen, clear, copy]);

  const forwardSelected = useCallback(() => {
    if (forwardable.length === 0) return;
    const batch = [...forwardable];
    clear();
    onForward(batch);
  }, [clear, forwardable, onForward]);

  const deleteSelected = useCallback(async () => {
    if (!upeerId || deletable.length === 0) return;
    let failed = 0;
    for (const id of deletable) {
      try {
        await deleteMessage(upeerId, id);
      } catch (cause) {
        failed += 1;
        console.warn('No se pudo eliminar un mensaje de la selección.', cause);
      }
    }
    clear();
    await onChanged();
    if (failed > 0) onError(failed === 1 ? 'Un mensaje no se pudo eliminar' : `${failed} mensajes no se pudieron eliminar`);
  }, [clear, deletable, onChanged, onError, upeerId]);

  return {
    active,
    chosen,
    clear,
    copySelected,
    deletableCount: deletable.length,
    deleteSelected,
    forwardSelected,
    forwardableCount: forwardable.length,
    selected,
    start,
    toggle,
  };
}
