import type { ChatTimelineEntry } from './chat-timeline';

export type ChatAnchor = {
  index: number;
  offset: number;
  viewPosition: number;
};

export const CHAT_ANCHOR_VIEW_POSITION = 0.5;
export const CHAT_ANCHOR_ROW_HEIGHT = 72;

export function resolveChatAnchor(
  timeline: readonly ChatTimelineEntry[],
  messageId: string | null | undefined,
  rowHeight: number = CHAT_ANCHOR_ROW_HEIGHT,
): ChatAnchor | null {
  if (!messageId) return null;
  const index = timeline.findIndex((entry) => entry.message.id === messageId);
  if (index < 0) return null;
  return { index, offset: index * rowHeight, viewPosition: CHAT_ANCHOR_VIEW_POSITION };
}
