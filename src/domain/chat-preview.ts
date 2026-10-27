import { describePresence } from './presence';

export type ConversationActivity = 'typing' | 'recording';

export type ChatPreviewInput = {
  preview: string;
  pending?: boolean;
  activity?: ConversationActivity | '';
  lastSeen?: number;
  now: number;
  onlineStatus: boolean;
  lastSeenEnabled: boolean;
};

export type ChatPreview = {
  text: string;
  accent: boolean;
  italic: boolean;
};

export function describeChatPreview({ preview, pending, activity, lastSeen, now, onlineStatus, lastSeenEnabled }: ChatPreviewInput): ChatPreview {
  if (pending) return { text: preview, accent: false, italic: true };
  if (activity === 'recording') return { text: 'Grabando audio…', accent: true, italic: false };
  if (activity === 'typing') return { text: 'Escribiendo…', accent: true, italic: false };
  if (preview) return { text: preview, accent: false, italic: false };
  return { text: describePresence({ lastSeen, now, onlineStatus, lastSeenEnabled }), accent: false, italic: false };
}
