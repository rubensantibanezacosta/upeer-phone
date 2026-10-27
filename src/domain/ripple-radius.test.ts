import { describe, expect, it } from 'vitest';

import { circularRippleRadius } from '@/domain/ripple-radius';

describe('circularRippleRadius', () => {
  it('redondea el ripple de un botón cuadrado', () => {
    expect(circularRippleRadius({ height: 44, width: 44 })).toBe(22);
    expect(circularRippleRadius({ height: 52, width: 52 })).toBe(26);
    expect(circularRippleRadius({ height: 40, width: 40 })).toBe(20);
  });

  it('usa el lado corto cuando el botón es casi cuadrado', () => {
    expect(circularRippleRadius({ height: 44, width: 40 })).toBe(20);
    expect(circularRippleRadius({ height: 32, width: 40 })).toBe(16);
  });

  it('deja el ripple rectangular en botones anchos o altos', () => {
    expect(circularRippleRadius({ height: 44, width: 300 })).toBeUndefined();
    expect(circularRippleRadius({ height: 56, width: 360 })).toBeUndefined();
    expect(circularRippleRadius({ height: 40, width: 60 })).toBeUndefined();
  });

  it('no adivina sin medidas numéricas', () => {
    expect(circularRippleRadius(undefined)).toBeUndefined();
    expect(circularRippleRadius({ height: 44 })).toBeUndefined();
    expect(circularRippleRadius({ height: '100%', width: 44 })).toBeUndefined();
    expect(circularRippleRadius({ height: 0, width: 0 })).toBeUndefined();
  });
});
