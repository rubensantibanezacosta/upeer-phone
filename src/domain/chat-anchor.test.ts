import { describe, expect, it } from 'vitest';

import { CHAT_ANCHOR_VIEW_POSITION, resolveChatAnchor } from './chat-anchor';
import type { ChatTimelineEntry } from './chat-timeline';

function entry(id: string): ChatTimelineEntry {
  return {
    message: { content: id, id, isMine: false, status: 'read', timestamp: 1, upeerId: 'peer' },
    endsGroup: false,
    showDateSeparator: false,
    startsGroup: false,
  };
}

describe('resolveChatAnchor', () => {
  it('encuentra un mensaje reciente en la parte alta del timeline invertido', () => {
    const timeline = [entry('nuevo'), entry('medio'), entry('viejo')];
    expect(resolveChatAnchor(timeline, 'nuevo')).toEqual({ index: 0, offset: 0, viewPosition: CHAT_ANCHOR_VIEW_POSITION });
  });

  it('encuentra un mensaje antiguo al fondo del timeline invertido', () => {
    const timeline = [entry('nuevo'), entry('medio'), entry('viejo')];
    expect(resolveChatAnchor(timeline, 'viejo')).toEqual({ index: 2, offset: 144, viewPosition: CHAT_ANCHOR_VIEW_POSITION });
  });

  it('usa la altura de fila indicada para estimar el offset', () => {
    const timeline = [entry('nuevo'), entry('viejo')];
    expect(resolveChatAnchor(timeline, 'viejo', 100)?.offset).toBe(100);
  });

  it('devuelve null cuando el mensaje no está en la ventana cargada', () => {
    expect(resolveChatAnchor([entry('nuevo')], 'perdido')).toBeNull();
  });

  it('devuelve null con el timeline vacío', () => {
    expect(resolveChatAnchor([], 'cualquiera')).toBeNull();
  });

  it('devuelve null sin identificador', () => {
    const timeline = [entry('nuevo')];
    expect(resolveChatAnchor(timeline, undefined)).toBeNull();
    expect(resolveChatAnchor(timeline, null)).toBeNull();
    expect(resolveChatAnchor(timeline, '')).toBeNull();
  });

  it('resuelve la primera aparición cuando el identificador está duplicado', () => {
    const timeline = [entry('repetido'), entry('repetido'), entry('viejo')];
    expect(resolveChatAnchor(timeline, 'repetido')?.index).toBe(0);
  });
});
