import { describePresence } from './presence';

export type ChatPreviewInput = {
  preview: string;
  pending?: boolean;
  typing?: boolean;
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

export function describeChatPreview({ preview, pending, typing, lastSeen, now, onlineStatus, lastSeenEnabled }: ChatPreviewInput): ChatPreview {
  if (pending) return { text: preview, accent: false, italic: true };
  if (typing) return { text: 'Escribiendo…', accent: true, italic: false };
  if (preview) return { text: preview, accent: false, italic: false };
  return { text: describePresence({ lastSeen, now, onlineStatus, lastSeenEnabled }), accent: false, italic: false };
}
