import { describe, expect, it } from 'vitest';

import { getTrustAnnouncement, getTrustWarning, isLowReputation, LOW_VOUCH_THRESHOLD } from './trust';

describe('isLowReputation', () => {
  it('marca como baja cualquier puntuación por debajo del umbral', () => {
    expect(isLowReputation(0)).toBe(true);
    expect(isLowReputation(LOW_VOUCH_THRESHOLD - 1)).toBe(true);
  });

  it('no marca la puntuación neutra ni las buenas', () => {
    expect(isLowReputation(LOW_VOUCH_THRESHOLD)).toBe(false);
    expect(isLowReputation(50)).toBe(false);
    expect(isLowReputation(100)).toBe(false);
  });

  it('no avisa cuando no hay dato', () => {
    expect(isLowReputation()).toBe(false);
    expect(isLowReputation(Number.NaN)).toBe(false);
    expect(isLowReputation(Number.POSITIVE_INFINITY)).toBe(false);
  });
});

describe('getTrustWarning', () => {
  it('describe el aviso solo cuando la reputación es muy mala', () => {
    expect(getTrustWarning(12)).toContain('12/100');
    expect(getTrustWarning(12)).toContain('Ten cuidado');
  });

  it('devuelve null en el resto de casos', () => {
    expect(getTrustWarning(39)).not.toBeNull();
    expect(getTrustWarning(40)).toBeNull();
    expect(getTrustWarning(50)).toBeNull();
    expect(getTrustWarning(95)).toBeNull();
    expect(getTrustWarning()).toBeNull();
  });
});

describe('getTrustAnnouncement', () => {
  it('antepone el aviso para lectores de pantalla y calla cuando no hay riesgo', () => {
    expect(getTrustAnnouncement(10)).toBe('Aviso: Baja reputación en la red (10/100). Ten cuidado.');
    expect(getTrustAnnouncement(80)).toBe('');
  });
});
