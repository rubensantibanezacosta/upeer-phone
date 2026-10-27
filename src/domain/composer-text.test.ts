import { describe, expect, it } from 'vitest';

import { deleteBeforeSelection, insertAtSelection, trackedSelection } from '@/domain/composer-text';

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

describe('deleteBeforeSelection', () => {
  it('borra el carácter anterior al cursor', () => {
    expect(deleteBeforeSelection('hola', { end: 4, start: 4 })).toEqual({
      selection: { end: 3, start: 3 },
      value: 'hol',
    });
  });

  it('borra el emoji entero y no medio par suplente', () => {
    expect(deleteBeforeSelection('hola🙂', { end: 6, start: 6 })).toEqual({
      selection: { end: 4, start: 4 },
      value: 'hola',
    });
  });

  it('borra en medio del texto sin tocar lo que va detrás', () => {
    expect(deleteBeforeSelection('abcde', { end: 3, start: 3 })).toEqual({
      selection: { end: 2, start: 2 },
      value: 'abde',
    });
  });

  it('vacía la selección cuando hay un rango marcado', () => {
    expect(deleteBeforeSelection('hola mundo', { end: 10, start: 5 })).toEqual({
      selection: { end: 5, start: 5 },
      value: 'hola ',
    });
  });

  it('no hace nada si el cursor está al principio', () => {
    expect(deleteBeforeSelection('hola', { end: 0, start: 0 })).toEqual({
      selection: { end: 0, start: 0 },
      value: 'hola',
    });
  });

  it('no hace nada si el texto está vacío', () => {
    expect(deleteBeforeSelection('', { end: 0, start: 0 })).toEqual({
      selection: { end: 0, start: 0 },
      value: '',
    });
  });
});

describe('trackedSelection', () => {
  it('conserva el cursor guardado mientras el panel de emojis manda', () => {
    expect(trackedSelection(true, { end: 4, start: 4 }, { end: 0, start: 0 }, true)).toEqual({ end: 4, start: 4 });
    expect(trackedSelection(true, { end: 4, start: 4 }, { end: 9, start: 9 }, true)).toEqual({ end: 4, start: 4 });
  });

  it('sigue al campo cuando ya no hay panel abierto', () => {
    expect(trackedSelection(false, { end: 4, start: 4 }, { end: 9, start: 9 }, true)).toEqual({ end: 9, start: 9 });
  });

  it('no pierde el cursor si el campo no informa de ninguna selección', () => {
    expect(trackedSelection(false, { end: 4, start: 4 }, null, true)).toEqual({ end: 4, start: 4 });
    expect(trackedSelection(false, { end: 4, start: 4 }, undefined, true)).toEqual({ end: 4, start: 4 });
  });

  it('ignora el cero que manda el campo al perder el foco', () => {
    expect(trackedSelection(false, { end: 4, start: 4 }, { end: 0, start: 0 }, false)).toEqual({ end: 4, start: 4 });
  });

  it('acepta el cero del campo cuando el usuario toca el principio', () => {
    expect(trackedSelection(false, { end: 4, start: 4 }, { end: 0, start: 0 }, true)).toEqual({ end: 0, start: 0 });
  });
});
