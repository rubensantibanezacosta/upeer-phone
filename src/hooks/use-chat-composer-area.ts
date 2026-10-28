import { useMemo, type ComponentProps } from 'react';

import type { ChatComposerArea } from '@/components/chat/chat-composer-area';
import { buildReplyPreview } from '@/domain/chat-reply';
import type { useChatComposer } from '@/hooks/use-chat-composer';

type ChatComposerHook = ReturnType<typeof useChatComposer>;

type ChatComposerAreaOptions = {
  alias: string;
  attach: () => void;
  attachFromCamera: () => void;
  composer: ChatComposerHook;
  hidden: boolean;
  onVoiceError: (message: string) => void;
  onVoiceNote: ComponentProps<typeof ChatComposerArea>['onVoiceNote'];
};

export function useChatComposerArea({ alias, attach, attachFromCamera, composer, hidden, onVoiceError, onVoiceNote }: ChatComposerAreaOptions): ComponentProps<typeof ChatComposerArea> {
  const {
    cancelContext,
    dismissPreview,
    editing,
    error,
    handleChange,
    linkPreview,
    loadingPreview,
    notifyActivity,
    replyingTo,
    submit,
    text,
  } = composer;

  return useMemo(() => ({
    editing: editing !== null,
    error,
    hidden,
    loadingPreview,
    onActivity: notifyActivity,
    onAttach: attach,
    onCamera: attachFromCamera,
    onCancelContext: cancelContext,
    onDismissPreview: dismissPreview,
    onChange: handleChange,
    onSend: () => { void submit(); },
    onVoiceError,
    onVoiceNote,
    preview: linkPreview,
    reply: buildReplyPreview(replyingTo ?? undefined, { mine: 'Tú', peer: alias || 'Contacto' }),
    value: text,
  }), [alias, attach, attachFromCamera, cancelContext, dismissPreview, editing, error, handleChange, hidden, linkPreview, loadingPreview, notifyActivity, onVoiceError, onVoiceNote, replyingTo, submit, text]);
}
