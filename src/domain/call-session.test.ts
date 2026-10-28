import { describe, expect, it } from 'vitest';

import {
  callDurationLabel,
  callEndLabel,
  callKindLabel,
  callPhaseLabel,
  canToggleMedia,
  shouldRing,
  shouldShowCall,
} from './call-session';

const base = {
  callId: 'a'.repeat(32),
  peerUpeerId: 'b'.repeat(64),
  kind: 'audio' as const,
  direction: 'incoming' as const,
  phase: 'incoming-ringing' as const,
  muted: false,
  cameraEnabled: false,
  startedAt: 1_000_000,
  connectedAt: 0,
  endedAt: 0,
  endReason: '',
};

describe('callPhaseLabel', () => {
  it('describe cada fase en curso', () => {
    expect(callPhaseLabel({ ...base, phase: 'outgoing-ringing' })).toBe('Llamando…');
    expect(callPhaseLabel(base)).toBe('Llamada entrante');
    expect(callPhaseLabel({ ...base, phase: 'negotiating' })).toBe('Conectando…');
    expect(callPhaseLabel({ ...base, phase: 'connected' })).toBe('En llamada');
  });

  it('al terminar explica el motivo', () => {
    expect(callPhaseLabel({ ...base, phase: 'ended', endReason: 'no-answer' })).toBe('Sin respuesta');
    expect(callPhaseLabel({ ...base, phase: 'ended', endReason: 'busy' })).toBe('Estaba ocupado');
  });
});

describe('callEndLabel', () => {
  it('traduce los motivos del protocolo', () => {
    expect(callEndLabel({ ...base, endReason: 'rejected' })).toBe('Llamada rechazada');
    expect(callEndLabel({ ...base, endReason: 'canceled' })).toBe('Llamada cancelada');
    expect(callEndLabel({ ...base, endReason: 'error' })).toBe('La llamada falló');
    expect(callEndLabel({ ...base, endReason: 'local-hangup' })).toBe('Llamada finalizada');
  });
});

describe('callKindLabel', () => {
  it('distingue voz de vídeo', () => {
    expect(callKindLabel(base)).toBe('Llamada de voz');
    expect(callKindLabel({ ...base, kind: 'video' })).toBe('Videollamada');
  });
});

describe('callDurationLabel', () => {
  it('cuenta desde que se conecta y se congela al terminar', () => {
    expect(callDurationLabel({ ...base, phase: 'connected', connectedAt: 1_000_000 }, 1_065_000)).toBe('01:05');
    expect(callDurationLabel({ ...base, phase: 'ended', connectedAt: 1_000_000, endedAt: 1_002_000 }, 9_999_999)).toBe('00:02');
  });

  it('mientras suena cuenta desde el inicio y nunca es negativo', () => {
    expect(callDurationLabel(base, 1_003_000)).toBe('00:03');
    expect(callDurationLabel(base, 999_000)).toBe('00:00');
  });
});

describe('shouldRing', () => {
  it('solo suena con una llamada entrante', () => {
    expect(shouldRing(base)).toBe(true);
    expect(shouldRing({ ...base, phase: 'outgoing-ringing' })).toBe(false);
    expect(shouldRing(null)).toBe(false);
  });
});

describe('shouldShowCall', () => {
  it('muestra la llamada también cuando acaba de terminar', () => {
    expect(shouldShowCall(base)).toBe(true);
    expect(shouldShowCall({ ...base, phase: 'ended' })).toBe(true);
    expect(shouldShowCall(null)).toBe(false);
  });
});

describe('canToggleMedia', () => {
  it('solo deja silenciar cuando la llamada ya está establecida', () => {
    expect(canToggleMedia(base)).toBe(false);
    expect(canToggleMedia({ ...base, phase: 'negotiating' })).toBe(true);
    expect(canToggleMedia({ ...base, phase: 'connected' })).toBe(true);
    expect(canToggleMedia(null)).toBe(false);
  });
});
