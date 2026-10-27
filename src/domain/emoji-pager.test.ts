import { describe, expect, it } from 'vitest';

import { clampPageIndex, emojiRowWindow, nextPageIndex, pageIndexFromOffset, pageOffset } from './emoji-pager';

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

  it('recorta la ventana de filas al principio y al final', () => {
    expect(emojiRowWindow(0, 46, 15, 3)).toEqual({ first: 0, last: 4 });
    expect(emojiRowWindow(46 * 5, 46, 15, 3)).toEqual({ first: 4, last: 8 });
    expect(emojiRowWindow(46 * 13, 46, 15, 3)).toEqual({ first: 12, last: 15 });
    expect(emojiRowWindow(99999, 46, 15, 3)).toEqual({ first: 12, last: 15 });
  });

  it('devuelve una ventana vacia sin filas', () => {
    expect(emojiRowWindow(0, 46, 0, 3)).toEqual({ first: 0, last: 0 });
    expect(emojiRowWindow(10, 0, 15, 3)).toEqual({ first: 0, last: 0 });
    expect(emojiRowWindow(Number.NaN, 46, 15, 3)).toEqual({ first: 0, last: 0 });
  });
});
