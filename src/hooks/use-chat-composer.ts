import { useCallback, useRef, useState } from 'react';

import { forwardResultMessage } from '@/domain/forward';
import { messageText } from '@/domain/message-preview';
import type { ProtocolMessage } from '../../modules/upeer-transport/src/UPeerTransport.types';
import { editChat, sendChat, sendTyping } from '../../modules/upeer-transport/src/UPeerTransportModule';

const TYPING_INTERVAL_MS = 3000;

type ChatComposerOptions = {
  onChanged: () => Promise<void>;
  onForwarded: (message: string) => void;
  upeerId?: string;
};

export function useChatComposer({ onChanged, onForwarded, upeerId }: ChatComposerOptions) {
  const [text, setText] = useState('');
  const [editing, setEditing] = useState<ProtocolMessage | null>(null);
  const [replyingTo, setReplyingTo] = useState<ProtocolMessage | null>(null);
  const [forwarding, setForwarding] = useState<ProtocolMessage | null>(null);
  const [error, setError] = useState('');
  const typingSentAtRef = useRef(0);

  const handleChange = useCallback((value: string) => {
    setText(value);
    if (!upeerId || !value.trim()) return;
    const now = Date.now();
    if (now - typingSentAtRef.current < TYPING_INTERVAL_MS) return;
    typingSentAtRef.current = now;
    sendTyping(upeerId).catch((cause: unknown) => {
      console.warn('No se pudo enviar el indicador de escritura.', cause);
    });
  }, [upeerId]);

  const submit = async () => {
    const content = text.trim();
    if (!content || !upeerId) return;
    const currentEdit = editing;
    const currentReply = replyingTo;
    setText('');
    setEditing(null);
    setReplyingTo(null);
    setError('');
    try {
      if (currentEdit) {
        await editChat(upeerId, currentEdit.id, content);
      } else {
        await sendChat(upeerId, content, currentReply?.id ?? '');
      }
      await onChanged();
    } catch (cause) {
      setText(content);
      setEditing(currentEdit);
      setReplyingTo(currentReply);
      setError(cause instanceof Error ? cause.message : 'No se pudo enviar el mensaje.');
    }
  };

  const startEditing = useCallback((message: ProtocolMessage) => {
    setReplyingTo(null);
    setEditing(message);
    setText(messageText(message.content));
    setError('');
  }, []);

  const startReplying = useCallback((message: ProtocolMessage) => {
    setEditing(null);
    setReplyingTo(message);
    setError('');
  }, []);

  const cancelContext = useCallback(() => {
    if (editing) setText('');
    setEditing(null);
    setReplyingTo(null);
  }, [editing]);

  const startForwarding = useCallback((message: ProtocolMessage) => {
    setForwarding(message);
    setError('');
  }, []);

  const handleForwarded = useCallback((count: number, failed: number) => {
    setForwarding(null);
    onForwarded(forwardResultMessage(count, failed));
  }, [onForwarded]);

  const cancelForwarding = useCallback(() => {
    setForwarding(null);
  }, []);

  return {
    cancelContext,
    cancelForwarding,
    editing,
    error,
    forwarding,
    handleChange,
    handleForwarded,
    replyingTo,
    setError,
    startEditing,
    startForwarding,
    startReplying,
    submit,
    text,
  };
}
