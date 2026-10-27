import { useCallback, useRef, useState } from 'react';

import { forwardResultMessage } from '@/domain/forward';
import { buildLinkPreviewPayload } from '@/domain/link-preview';
import { messageText } from '@/domain/message-preview';
import { decideTypingSignal } from '@/domain/typing-activity';
import { useLinkPreview } from '@/hooks/use-link-preview';
import type { ConversationActivityState, ProtocolMessage } from '../../modules/upeer-transport/src/UPeerTransport.types';
import { editChat, sendChat, sendTyping } from '../../modules/upeer-transport/src/UPeerTransportModule';

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
  const link = useLinkPreview(editing ? '' : text);

  const notifyActivity = useCallback((state: ConversationActivityState) => {
    if (!upeerId) return;
    if (state === 'stop') typingSentAtRef.current = 0;
    sendTyping(upeerId, state).catch((cause: unknown) => {
      console.warn('No se pudo enviar el indicador de actividad.', cause);
    });
  }, [upeerId]);

  const handleChange = useCallback((value: string) => {
    setText(value);
    if (!upeerId) return;
    const now = Date.now();
    const signal = decideTypingSignal({ hasText: value.trim().length > 0, lastSentAt: typingSentAtRef.current, now });
    if (!signal) return;
    if (signal === 'typing') typingSentAtRef.current = now;
    notifyActivity(signal);
  }, [notifyActivity, upeerId]);

  const submit = async () => {
    const content = text.trim();
    if (!content || !upeerId) return;
    const currentEdit = editing;
    const currentReply = replyingTo;
    setText('');
    setEditing(null);
    setReplyingTo(null);
    setError('');
    link.clear();
    if (typingSentAtRef.current > 0) notifyActivity('stop');
    try {
      if (currentEdit) {
        await editChat(upeerId, currentEdit.id, content);
      } else {
        await sendChat(upeerId, buildLinkPreviewPayload(content, link.preview), currentReply?.id ?? '');
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
    dismissPreview: link.dismiss,
    editing,
    error,
    forwarding,
    handleChange,
    handleForwarded,
    linkPreview: link.preview,
    loadingPreview: link.loading,
    notifyActivity,
    replyingTo,
    setError,
    startEditing,
    startForwarding,
    startReplying,
    submit,
    text,
  };
}
