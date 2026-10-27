import { describe, expect, it } from 'vitest';

import { clampPageIndex, nextPageIndex } from './emoji-pager';

describe('emoji pager', () => {
  it('clampa un indice suelto', () => {
    expect(clampPageIndex(4, 9)).toBe(4);
    expect(clampPageIndex(12, 9)).toBe(8);
    expect(clampPageIndex(-3, 9)).toBe(0);
    expect(clampPageIndex(Number.NaN, 9)).toBe(0);
  });

  it('devuelve cero sin paginas', () => {
    expect(clampPageIndex(2, 0)).toBe(0);
    expect(nextPageIndex(0, 1, 0)).toBe(0);
  });

  it('avanza y retrocede una pagina', () => {
    expect(nextPageIndex(1, 1, 9)).toBe(2);
    expect(nextPageIndex(1, -1, 9)).toBe(0);
  });

  it('no se sale del rango al avanzar o retroceder', () => {
    expect(nextPageIndex(8, 1, 9)).toBe(8);
    expect(nextPageIndex(0, -1, 9)).toBe(0);
    expect(nextPageIndex(0, 9, 4)).toBe(3);
  });
});
