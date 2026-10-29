import { describePresence } from './presence';

export type ConversationActivity = 'typing' | 'recording';

export type ChatPreviewInput = {
  preview: string;
  pending?: boolean;
  activity?: ConversationActivity | '';
  lastActive?: number;
  now: number;
  shareActivity: boolean;
};

export type ChatPreview = {
  text: string;
  accent: boolean;
  italic: boolean;
};

export function describeChatPreview({ preview, pending, activity, lastActive, now, shareActivity }: ChatPreviewInput): ChatPreview {
  if (pending) return { text: preview, accent: false, italic: true };
  if (activity === 'recording') return { text: 'Grabando audio…', accent: true, italic: false };
  if (activity === 'typing') return { text: 'Escribiendo…', accent: true, italic: false };
  if (preview) return { text: preview, accent: false, italic: false };
  return { text: describePresence({ lastActive, now, shareActivity }) ?? '', accent: false, italic: false };
}
