import { describe, expect, it } from 'vitest';

import { ACTIVITY_HEARTBEAT_MS, ACTIVITY_THROTTLE_MS, shouldReportActivity } from './activity-signal';

const NOW = 1_700_000_000_000;

describe('shouldReportActivity', () => {
  it('siempre anuncia al volver a primer plano', () => {
    expect(shouldReportActivity('resume', NOW, NOW + 1)).toBe(true);
    expect(shouldReportActivity('resume', 0, NOW)).toBe(true);
  });

  it('anuncia el primer gesto sin historial', () => {
    expect(shouldReportActivity('gesture', NOW - ACTIVITY_THROTTLE_MS, NOW)).toBe(true);
  });

  it('descarta gestos dentro del throttle', () => {
    expect(shouldReportActivity('gesture', NOW - ACTIVITY_THROTTLE_MS + 1, NOW)).toBe(false);
  });

  it('acepta el gesto justo en el límite del throttle', () => {
    expect(shouldReportActivity('gesture', NOW - ACTIVITY_THROTTLE_MS, NOW)).toBe(true);
  });

  it('el latido espera más que el throttle', () => {
    expect(shouldReportActivity('heartbeat', NOW - ACTIVITY_THROTTLE_MS, NOW)).toBe(false);
    expect(shouldReportActivity('heartbeat', NOW - ACTIVITY_HEARTBEAT_MS, NOW)).toBe(true);
  });

  it('el latido cabe dentro de la ventana en línea de 65 s', () => {
    expect(ACTIVITY_HEARTBEAT_MS).toBeLessThan(60_000);
    expect(ACTIVITY_HEARTBEAT_MS).toBeGreaterThanOrEqual(ACTIVITY_THROTTLE_MS);
  });

  it('un reloj que retrocede no bloquea el anuncio', () => {
    expect(shouldReportActivity('gesture', NOW + 5_000, NOW)).toBe(true);
  });

  it('un valor inicial inválido anuncia inmediatamente', () => {
    for (const value of [0, -1, Number.NaN, Number.POSITIVE_INFINITY]) {
      expect(shouldReportActivity('gesture', value, NOW)).toBe(true);
    }
  });
});
