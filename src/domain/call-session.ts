import type { CallState } from '../../modules/upeer-transport/src/UPeerTransportModule';

export type CallView = CallState;

export function callPhaseLabel(call: CallView): string {
  if (call.phase === 'outgoing-ringing') return 'Llamando…';
  if (call.phase === 'incoming-ringing') return 'Llamada entrante';
  if (call.phase === 'negotiating') return 'Conectando…';
  if (call.phase === 'connected') return 'En llamada';
  return callEndLabel(call);
}

export function callEndLabel(call: CallView): string {
  if (call.endReason === 'rejected') return 'Llamada rechazada';
  if (call.endReason === 'busy') return 'Estaba ocupado';
  if (call.endReason === 'canceled') return 'Llamada cancelada';
  if (call.endReason === 'no-answer') return 'Sin respuesta';
  if (call.endReason === 'error') return 'La llamada falló';
  return 'Llamada finalizada';
}

export function callKindLabel(call: CallView): string {
  return call.kind === 'video' ? 'Videollamada' : 'Llamada de voz';
}

export function callDurationLabel(call: CallView, now: number): string {
  const from = call.connectedAt > 0 ? call.connectedAt : call.startedAt;
  const until = call.endedAt > 0 ? call.endedAt : now;
  const total = Math.max(0, Math.floor((until - from) / 1000));
  const minutes = Math.floor(total / 60);
  const seconds = total % 60;
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
}

export function shouldRing(call: CallView | null): boolean {
  return Boolean(call && call.phase === 'incoming-ringing');
}

export function isCallOver(call: CallView | null): boolean {
  return Boolean(call && call.phase === 'ended');
}

export function shouldShowCall(call: CallView | null): boolean {
  return Boolean(call);
}

/** Los botones de silencio y cámara solo tienen sentido cuando la llamada ya está establecida. */
export function canToggleMedia(call: CallView | null): boolean {
  return Boolean(call && (call.phase === 'negotiating' || call.phase === 'connected'));
}

/** Sin media en el móvil: el estado se muestra, pero el audio se anuncia como pendiente. */
export const CALL_AUDIO_NOTICE = 'El audio todavía no está disponible en este cliente';
