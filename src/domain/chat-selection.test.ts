import { describe, expect, it } from 'vitest';

import {
  deletableIds,
  describeSelection,
  forwardableMessages,
  orderedSelection,
  selectionExcerpt,
  toggleSelected,
  type SelectableMessage,
} from './chat-selection';

type Message = SelectableMessage & { timestamp: number };

const message = (id: string, isMine: boolean, timestamp: number, extra: Partial<Message> = {}): Message => ({
  deleted: false,
  id,
  isMine,
  timestamp,
  ...extra,
});

const messages: Message[] = [
  message('a', true, 300),
  message('b', false, 100),
  message('c', true, 200, { deleted: true }),
  message('d', true, 400),
];

describe('toggleSelected', () => {
  it('añade y quita sin mutar el conjunto original', () => {
    const start: ReadonlySet<string> = new Set(['a']);
    const added = toggleSelected(start, 'b');
    expect([...added]).toEqual(['a', 'b']);
    expect([...start]).toEqual(['a']);
    expect([...toggleSelected(added, 'a')]).toEqual(['b']);
  });
});

describe('describeSelection', () => {
  it('concuerda en singular y plural', () => {
    expect(describeSelection(0)).toBe('Ningún mensaje');
    expect(describeSelection(1)).toBe('1 seleccionado');
    expect(describeSelection(4)).toBe('4 seleccionados');
  });
});

describe('orderedSelection', () => {
  it('devuelve los mensajes en el orden de la conversación', () => {
    expect(orderedSelection(messages, new Set(['d', 'b'])).map((entry) => entry.id)).toEqual(['b', 'd']);
  });
});

describe('deletableIds', () => {
  it('solo deja borrar los propios y vivos', () => {
    expect(deletableIds(messages, new Set(['a', 'b', 'c', 'd']))).toEqual(['a', 'd']);
  });

  it('no devuelve nada sin selección', () => {
    expect(deletableIds(messages, new Set())).toEqual([]);
  });
});

describe('forwardableMessages', () => {
  it('ordena por fecha y descarta los eliminados', () => {
    expect(forwardableMessages(messages, new Set(['d', 'c', 'b', 'a'])).map((entry) => entry.id)).toEqual(['b', 'a', 'd']);
  });

  it('respeta el orden de la lista cuando dos mensajes comparten fecha', () => {
    const same = [message('x', true, 5), message('y', false, 5)];
    expect(forwardableMessages(same, new Set(['y', 'x'])).map((entry) => entry.id)).toEqual(['x', 'y']);
  });
});

describe('selectionExcerpt', () => {
  it('une las líneas y recorta con puntos suspensivos', () => {
    expect(selectionExcerpt(['uno', 'dos'])).toBe('uno\ndos');
    expect(selectionExcerpt(['a'.repeat(10)], 4)).toBe('aaaa…');
  });

  it('devuelve vacío sin contenido', () => {
    expect(selectionExcerpt([])).toBe('');
    expect(selectionExcerpt(['   '])).toBe('');
  });
});
