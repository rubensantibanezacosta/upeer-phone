import { describe, expect, it } from 'vitest';

import { highlightSegments } from './search-highlight';

describe('highlightSegments', () => {
  it('devuelve el texto entero sin término', () => {
    expect(highlightSegments('Ana Pérez', '  ')).toEqual([{ text: 'Ana Pérez', match: false }]);
  });

  it('marca el término aunque lleve acentos en el texto', () => {
    expect(highlightSegments('Ana Pérez', 'perez')).toEqual([
      { text: 'Ana ', match: false },
      { text: 'Pérez', match: true },
    ]);
  });

  it('marca el término aunque el usuario escriba el acento', () => {
    expect(highlightSegments('Grupo vecinos de Peña', 'peña')).toEqual([
      { text: 'Grupo vecinos de ', match: false },
      { text: 'Peña', match: true },
    ]);
  });

  it('marca todas las apariciones', () => {
    expect(highlightSegments('aNa y ana', 'ana')).toEqual([
      { text: 'aNa', match: true },
      { text: ' y ', match: false },
      { text: 'ana', match: true },
    ]);
  });

  it('ignora mayúsculas y conserva el texto original', () => {
    expect(highlightSegments('BRUNO', 'bruno')).toEqual([{ text: 'BRUNO', match: true }]);
  });

  it('no devuelve nada para texto vacío', () => {
    expect(highlightSegments('', 'ana')).toEqual([]);
  });
});
