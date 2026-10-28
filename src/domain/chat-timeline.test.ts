import { describe, expect, it } from 'vitest';

import { buildChatTimeline, mergeChatHistory, toInvertedChatTimeline, type ChatTimelineEntry } from './chat-timeline';
import type { ProtocolMessage } from '../../modules/upeer-transport/src/UPeerTransport.types';

const PEER = 'a'.repeat(64);

function message(id: string, timestamp: number, isMine = false): ProtocolMessage {
  return { id, upeerId: PEER, isMine, content: id, status: isMine ? 'sent' : 'delivered', timestamp };
}

const firstDay = new Date(2026, 9, 20, 10, 0, 0).getTime();
const secondDay = new Date(2026, 9, 21, 9, 0, 0).getTime();

const flags = (entries: ChatTimelineEntry[]) => entries.map((entry) => ({
  id: entry.message.id,
  separator: entry.showDateSeparator,
  starts: entry.startsGroup,
  ends: entry.endsGroup,
}));

describe('chat timeline', () => {
  it('orders messages chronologically and groups consecutive messages from the same sender', () => {
    const timeline = buildChatTimeline([
      message('m3', firstDay + 120_000, true),
      message('m1', firstDay),
      message('m2', firstDay + 60_000),
    ]);

    expect(flags(timeline)).toEqual([
      { id: 'm1', separator: true, starts: true, ends: false },
      { id: 'm2', separator: false, starts: false, ends: true },
      { id: 'm3', separator: false, starts: true, ends: true },
    ]);
  });

  it('starts a new date separator and group when the day changes', () => {
    const timeline = buildChatTimeline([message('m1', firstDay), message('m2', secondDay, true)]);

    expect(flags(timeline)).toEqual([
      { id: 'm1', separator: true, starts: true, ends: true },
      { id: 'm2', separator: true, starts: true, ends: true },
    ]);
  });

  it('reverses the chronological timeline for the inverted list without mutating it', () => {
    const timeline = buildChatTimeline([message('m1', firstDay), message('m2', firstDay + 60_000)]);
    const inverted = toInvertedChatTimeline(timeline);

    expect(inverted.map((entry) => entry.message.id)).toEqual(['m2', 'm1']);
    expect(timeline.map((entry) => entry.message.id)).toEqual(['m1', 'm2']);
  });
});

describe('mergeChatHistory', () => {
  it('añade la página antigua sin duplicar lo ya cargado', () => {
    const merged = mergeChatHistory([message('m3', firstDay + 120_000)], [message('m1', firstDay), message('m2', firstDay + 60_000)]);

    expect(merged.map((item) => item.id)).toEqual(['m3', 'm1', 'm2']);
  });

  it('descarta los mensajes repetidos al solapar páginas', () => {
    const merged = mergeChatHistory([message('m2', firstDay + 60_000), message('m3', firstDay + 120_000)], [message('m1', firstDay), message('m2', firstDay + 60_000)]);

    expect(merged.map((item) => item.id)).toEqual(['m2', 'm3', 'm1']);
  });

  it('no muta el historial recibido', () => {
    const current = [message('m2', firstDay + 60_000)];
    mergeChatHistory(current, [message('m1', firstDay)]);

    expect(current.map((item) => item.id)).toEqual(['m2']);
  });
});
