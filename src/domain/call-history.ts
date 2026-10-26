import type { ProtocolMessage } from '../../modules/upeer-transport/src/UPeerTransport.types';
import { parseSystemMessage } from '@/domain/system-message';

export type RecentCall = {
  id: string;
  chatId: string;
  timestamp: number;
  kind: 'audio' | 'video';
  duration: string | null;
  outcome: 'missed' | 'rejected' | null;
  isMine: boolean;
};

export type CallBatch = {
  chatId: string;
  messages: ProtocolMessage[];
};

const DEFAULT_LIMIT = 30;

export function parseRecentCall(message: ProtocolMessage, chatId: string): RecentCall | null {
  const call = parseSystemMessage(message.content)?.call;
  if (!call) return null;
  const timestamp = call.startedAt ?? message.timestamp;
  return {
    id: message.id || `${chatId}-${timestamp}`,
    chatId,
    timestamp: Number.isFinite(timestamp) ? timestamp : 0,
    kind: call.kind,
    duration: call.duration,
    outcome: call.outcome,
    isMine: message.isMine === true,
  };
}

export function collectRecentCalls(batches: CallBatch[], limit = DEFAULT_LIMIT): RecentCall[] {
  const seen = new Set<string>();
  const calls: RecentCall[] = [];
  for (const batch of batches) {
    for (const message of batch.messages) {
      const call = parseRecentCall(message, batch.chatId);
      if (!call || seen.has(call.id)) continue;
      seen.add(call.id);
      calls.push(call);
    }
  }
  return calls.sort((left, right) => right.timestamp - left.timestamp).slice(0, limit);
}

export function recentCallKindLabel(call: RecentCall): string {
  return call.kind === 'video' ? 'Videollamada' : 'Llamada de voz';
}

export function recentCallResultLabel(call: RecentCall): string {
  if (call.duration) return call.duration;
  return call.outcome === 'rejected' ? 'Rechazada' : 'Perdida';
}

export function recentCallMissed(call: RecentCall): boolean {
  return !call.duration && call.outcome !== 'rejected' && !call.isMine;
}
