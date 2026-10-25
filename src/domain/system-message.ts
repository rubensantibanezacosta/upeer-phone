export type CallSystemEvent = {
  kind: 'audio' | 'video';
  duration: string | null;
  startedAt: number | null;
  outcome: 'missed' | 'rejected' | null;
};

export type SystemMessage = {
  text: string;
  call: CallSystemEvent | null;
};

const PREFIX = '__SYS__|';

const OUTCOME_MATCH = /^(Llamada de voz|Videollamada) (perdida|rechazada)$/;
const DURATION_MATCH = /^(Llamada de voz|Videollamada) · (\d{1,}:\d{2})(?: · (\d{10,}))?$/;

export function parseCallSystemEvent(message: string): CallSystemEvent | null {
  const outcome = OUTCOME_MATCH.exec(message);
  if (outcome) {
    return {
      kind: outcome[1] === 'Videollamada' ? 'video' : 'audio',
      duration: null,
      startedAt: null,
      outcome: outcome[2] === 'rechazada' ? 'rejected' : 'missed',
    };
  }
  const timed = DURATION_MATCH.exec(message);
  if (!timed) return null;
  return {
    kind: timed[1] === 'Videollamada' ? 'video' : 'audio',
    duration: timed[2],
    startedAt: timed[3] ? Number(timed[3]) : null,
    outcome: null,
  };
}

export function parseSystemMessage(content: string): SystemMessage | null {
  if (!content.startsWith(PREFIX)) return null;
  const text = content.slice(PREFIX.length).trim();
  if (!text) return null;
  return { text, call: parseCallSystemEvent(text) };
}

export function callEventSummary(event: CallSystemEvent): string {
  const label = event.kind === 'video' ? 'Videollamada' : 'Llamada de voz';
  if (event.duration) return `${label} · ${event.duration}`;
  return event.outcome === 'rejected' ? `${label} rechazada` : `${label} perdida`;
}

export function callEventOutcome(event: CallSystemEvent): string {
  if (event.duration) return `Duración ${event.duration}`;
  return event.outcome === 'rejected' ? 'Llamada rechazada' : 'Llamada perdida';
}

export function callEventLabel(event: CallSystemEvent): string {
  return event.kind === 'video' ? 'Videollamada' : 'Llamada de voz';
}

export function systemMessageText(content: string): string | null {
  const parsed = parseSystemMessage(content);
  if (!parsed) return null;
  return parsed.call ? callEventSummary(parsed.call) : parsed.text;
}
