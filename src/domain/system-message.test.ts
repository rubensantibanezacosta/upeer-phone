import { describe, expect, it } from 'vitest';

import {
  callEventLabel,
  callEventOutcome,
  callEventSummary,
  parseCallSystemEvent,
  parseSystemMessage,
  systemMessageText,
} from './system-message';

describe('parseCallSystemEvent', () => {
  it('lee una llamada con duracion y marca de tiempo', () => {
    expect(parseCallSystemEvent('Llamada de voz · 2:31 · 1730000000000')).toEqual({
      kind: 'audio',
      duration: '2:31',
      startedAt: 1730000000000,
      outcome: null,
    });
  });

  it('lee una videollamada sin marca de tiempo', () => {
    expect(parseCallSystemEvent('Videollamada · 12:05')).toEqual({
      kind: 'video',
      duration: '12:05',
      startedAt: null,
      outcome: null,
    });
  });

  it('distingue perdida y rechazada', () => {
    expect(parseCallSystemEvent('Llamada de voz perdida')?.outcome).toBe('missed');
    expect(parseCallSystemEvent('Videollamada rechazada')?.outcome).toBe('rejected');
  });

  it('no confunde texto libre con una llamada', () => {
    expect(parseCallSystemEvent('Has añadido a Ivan')).toBeNull();
    expect(parseCallSystemEvent('Llamada de voz')).toBeNull();
  });
});

describe('parseSystemMessage', () => {
  it('exige el prefijo __SYS__', () => {
    expect(parseSystemMessage('Llamada de voz · 2:31')).toBeNull();
  });

  it('separa el texto del evento de llamada', () => {
    expect(parseSystemMessage('__SYS__|Llamada de voz · 2:31 · 1730000000000')).toEqual({
      text: 'Llamada de voz · 2:31 · 1730000000000',
      call: { kind: 'audio', duration: '2:31', startedAt: 1730000000000, outcome: null },
    });
  });

  it('acepta avisos de grupo sin evento de llamada', () => {
    expect(parseSystemMessage('__SYS__|Ivan creó el grupo')).toEqual({ text: 'Ivan creó el grupo', call: null });
  });

  it('ignora el prefijo vacio', () => {
    expect(parseSystemMessage('__SYS__|   ')).toBeNull();
  });
});

describe('etiquetas de llamada', () => {
  const call = { kind: 'audio' as const, duration: '2:31', startedAt: null, outcome: null };

  it('resume como la lista de escritorio', () => {
    expect(callEventSummary(call)).toBe('Llamada de voz · 2:31');
    expect(callEventSummary({ ...call, duration: null, outcome: 'missed' })).toBe('Llamada de voz perdida');
    expect(callEventSummary({ ...call, duration: null, outcome: 'rejected' })).toBe('Llamada de voz rechazada');
  });

  it('describe el resultado en la tarjeta', () => {
    expect(callEventOutcome(call)).toBe('Duración 2:31');
    expect(callEventOutcome({ ...call, duration: null, outcome: 'missed' })).toBe('Llamada perdida');
  });

  it('nombra el tipo de llamada', () => {
    expect(callEventLabel(call)).toBe('Llamada de voz');
    expect(callEventLabel({ ...call, kind: 'video' })).toBe('Videollamada');
  });
});

describe('systemMessageText', () => {
  it('devuelve el resumen para la vista previa', () => {
    expect(systemMessageText('__SYS__|Videollamada · 1:02 · 1730000000000')).toBe('Videollamada · 1:02');
    expect(systemMessageText('__SYS__|Ivan creó el grupo')).toBe('Ivan creó el grupo');
  });
});
