import { describe, expect, it } from 'vitest';

import { insertAtSelection } from '@/domain/composer-text';

describe('insertAtSelection', () => {
  it('inserta en el cursor y deja el cursor tras el texto insertado', () => {
    expect(insertAtSelection('hola mundo', { end: 4, start: 4 }, '🙂')).toEqual({
      selection: { end: 6, start: 6 },
      value: 'hola🙂 mundo',
    });
  });

  it('inserta al principio cuando el cursor está en cero', () => {
    expect(insertAtSelection('adios', { end: 0, start: 0 }, '🔥')).toEqual({
      selection: { end: 2, start: 2 },
      value: '🔥adios',
    });
  });

  it('reemplaza la selección activa', () => {
    expect(insertAtSelection('hola mundo', { end: 10, start: 5 }, '🌍')).toEqual({
      selection: { end: 7, start: 7 },
      value: 'hola 🌍',
    });
  });

  it('añade al final cuando el input está vacío', () => {
    expect(insertAtSelection('', { end: 0, start: 0 }, '😀')).toEqual({
      selection: { end: 2, start: 2 },
      value: '😀',
    });
  });

  it('recorta un cursor fuera del texto', () => {
    expect(insertAtSelection('hey', { end: 99, start: 99 }, '!')).toEqual({
      selection: { end: 4, start: 4 },
      value: 'hey!',
    });
  });

  it('normaliza una selección invertida', () => {
    expect(insertAtSelection('abc', { end: 1, start: 3 }, 'x')).toEqual({
      selection: { end: 4, start: 4 },
      value: 'abcx',
    });
  });
});
