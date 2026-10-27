import { describe, expect, it } from 'vitest';

import { clampPageIndex, nextPageIndex, pageIndexFromOffset, pageOffset, shouldMountPage } from './emoji-pager';

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

  it('deduce la pagina ya asentada', () => {
    expect(pageIndexFromOffset(0, 360, 9)).toBe(0);
    expect(pageIndexFromOffset(360, 360, 9)).toBe(1);
    expect(pageIndexFromOffset(719, 360, 9)).toBe(2);
    expect(pageIndexFromOffset(-400, 360, 9)).toBe(0);
    expect(pageIndexFromOffset(99999, 360, 9)).toBe(8);
    expect(pageIndexFromOffset(100, 0, 9)).toBe(0);
  });

  it('convierte indice en desplazamiento', () => {
    expect(pageOffset(0, 360, 9)).toBe(0);
    expect(pageOffset(3, 360, 9)).toBe(1080);
    expect(pageOffset(20, 360, 9)).toBe(8 * 360);
    expect(pageOffset(3, 0, 9)).toBe(0);
  });

  it('solo monta la pagina activa y sus vecinas', () => {
    expect(shouldMountPage(4, 4)).toBe(true);
    expect(shouldMountPage(3, 4)).toBe(true);
    expect(shouldMountPage(5, 4)).toBe(true);
    expect(shouldMountPage(2, 4)).toBe(false);
    expect(shouldMountPage(6, 4)).toBe(false);
  });
});
