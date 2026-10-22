import type { ProtocolMessage } from '../../modules/upeer-transport/src/UPeerTransport.types';

export type ChatTimelineEntry = {
  message: ProtocolMessage;
  showDateSeparator: boolean;
  startsGroup: boolean;
  endsGroup: boolean;
};

const timestampOf = (message: ProtocolMessage) => Number.isFinite(message.timestamp) ? message.timestamp : 0;

const dayKey = (timestamp: number) => {
  if (!Number.isFinite(timestamp)) return 'invalid';
  const date = new Date(timestamp);
  return `${date.getFullYear()}-${date.getMonth()}-${date.getDate()}`;
};

const sameSender = (left: ProtocolMessage, right: ProtocolMessage) =>
  left.isMine === right.isMine && left.upeerId === right.upeerId;

export function buildChatTimeline(messages: ProtocolMessage[]): ChatTimelineEntry[] {
  const ordered = [...messages].sort((left, right) => timestampOf(left) - timestampOf(right));
  return ordered.map((message, index) => {
    const previous = ordered[index - 1];
    const next = ordered[index + 1];
    const showDateSeparator = !previous || dayKey(previous.timestamp) !== dayKey(message.timestamp);
    return {
      message,
      showDateSeparator,
      startsGroup: showDateSeparator || !previous || !sameSender(previous, message),
      endsGroup: !next || dayKey(next.timestamp) !== dayKey(message.timestamp) || !sameSender(next, message),
    };
  });
}

export function toInvertedChatTimeline(entries: ChatTimelineEntry[]): ChatTimelineEntry[] {
  return [...entries].reverse();
}

export function formatChatDateLabel(timestamp: number): string {
  const currentDate = new Date(timestamp);
  if (!Number.isFinite(timestamp)) return '';
  const now = new Date();
  const yesterday = new Date(now);
  yesterday.setDate(now.getDate() - 1);
  if (currentDate.toDateString() === now.toDateString()) return 'Hoy';
  if (currentDate.toDateString() === yesterday.toDateString()) return 'Ayer';
  if (currentDate.getFullYear() === now.getFullYear()) return currentDate.toLocaleDateString([], { day: '2-digit', month: 'long' });
  return currentDate.toLocaleDateString([], { day: '2-digit', month: 'long', year: 'numeric' });
}

export function formatChatTime(timestamp: number): string {
  if (!Number.isFinite(timestamp)) return '';
  return new Date(timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}
