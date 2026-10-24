import { describe, expect, it } from 'vitest';

import { describeFontScale, scaleFontSize, TEXT_SCALE_FACTORS } from './text-scale';

describe('scaleFontSize', () => {
  it('deja el tamaño intacto en el valor medio', () => {
    expect(scaleFontSize(14, 'medium')).toBe(14);
    expect(scaleFontSize(11, 'medium')).toBe(11);
  });

  it('reduce y amplía en los extremos', () => {
    expect(scaleFontSize(20, 'small')).toBe(18);
    expect(scaleFontSize(20, 'large')).toBe(24);
  });

  it('redondea a píxeles enteros', () => {
    expect(scaleFontSize(13, 'small')).toBe(12);
    expect(scaleFontSize(13, 'large')).toBe(15);
  });

  it('nunca baja del mínimo legible', () => {
    expect(scaleFontSize(4, 'small')).toBe(9);
    expect(scaleFontSize(0, 'medium')).toBe(9);
  });

  it('mantiene el orden entre escalas', () => {
    const small = scaleFontSize(16, 'small');
    const medium = scaleFontSize(16, 'medium');
    const large = scaleFontSize(16, 'large');
    expect(small).toBeLessThan(medium);
    expect(medium).toBeLessThan(large);
  });
});

describe('describeFontScale', () => {
  it('describe el tamaño normal sin porcentaje', () => {
    expect(describeFontScale('medium')).toBe('Tamaño normal');
  });

  it('describe los extremos en porcentaje', () => {
    expect(describeFontScale('small')).toBe('Texto al 90 %');
    expect(describeFontScale('large')).toBe('Texto al 118 %');
  });

  it('el factor medio es exactamente uno', () => {
    expect(TEXT_SCALE_FACTORS.medium).toBe(1);
  });
});
