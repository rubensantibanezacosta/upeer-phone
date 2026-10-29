import { describe, expect, it } from 'vitest';

import { INDETERMINATE_PERCENT, clampPercent, ringGeometry } from './circular-progress';

describe('anillo de progreso circular', () => {
  it('deja el trazo centrado en el borde del círculo', () => {
    const ring = ringGeometry(40, 6, 0);

    expect(ring.center).toBe(20);
    expect(ring.radius).toBe(17);
    expect(ring.circumference).toBeCloseTo(2 * Math.PI * 17, 6);
  });

  it('a tamaño pequeño el grosor no se come el radio', () => {
    expect(ringGeometry(10, 40, 50).radius).toBe(2.5);
    expect(ringGeometry(44, 5, 50).radius).toBe(19.5);
  });

  it('sin avance el trazo está entero desplazado', () => {
    const ring = ringGeometry(80, 3, 0);

    expect(ring.dashOffset).toBeCloseTo(ring.circumference, 6);
  });

  it('a mitad de avance se pinta media circunferencia', () => {
    const ring = ringGeometry(80, 3, 50);

    expect(ring.dashOffset).toBeCloseTo(ring.circumference / 2, 6);
  });

  it('al terminar no queda desplazamiento', () => {
    expect(ringGeometry(80, 3, 100).dashOffset).toBeCloseTo(0, 6);
  });

  it('el avance se recorta entre 0 y 100', () => {
    expect(clampPercent(-20)).toBe(0);
    expect(clampPercent(180)).toBe(100);
    expect(clampPercent(Number.NaN)).toBe(0);
    expect(clampPercent(undefined)).toBe(0);
    expect(ringGeometry(40, 6, 150).percent).toBe(100);
  });

  it('el modo indeterminado usa un cuarto de vuelta', () => {
    const ring = ringGeometry(80, 3, INDETERMINATE_PERCENT);

    expect(ring.dashOffset).toBeCloseTo(ring.circumference * 0.75, 6);
  });
});
