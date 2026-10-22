import { describe, expect, it } from 'vitest';

import { getTrustDescriptor, getTrustLabel } from './trust';

describe('getTrustDescriptor', () => {
  it('marca como riesgo cualquier puntuación inferior a 40', () => {
    expect(getTrustDescriptor(0).kind).toBe('alert');
    expect(getTrustDescriptor(39).kind).toBe('alert');
  });

  it('mantiene la reputación estándar entre 40 y 64', () => {
    expect(getTrustDescriptor(40).kind).toBe('shield');
    expect(getTrustDescriptor(64).kind).toBe('shield');
  });

  it('marca como confiable desde 65 y como muy confiable desde 80', () => {
    expect(getTrustDescriptor(65).kind).toBe('check');
    expect(getTrustDescriptor(79).kind).toBe('check');
    expect(getTrustDescriptor(80).kind).toBe('verified');
    expect(getTrustDescriptor(100).kind).toBe('verified');
  });

  it('identifica el estado neutro de un contacto sin avales', () => {
    expect(getTrustDescriptor().kind).toBe('new');
    expect(getTrustDescriptor(50).kind).toBe('new');
    expect(getTrustLabel()).toBe('Sin historial de red aún');
  });
});
