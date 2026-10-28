import { describe, expect, it } from 'vitest';

import {
  appendChatSearchResults,
  CHAT_SEARCH_PAGE_SIZE,
  describeSearchPosition,
  resolveSearchStep,
  searchExcerpt,
  searchPageHasMore,
  toChatSearchResults,
  type ChatSearchResult,
} from './chat-search';
import type { MessageSearchHit } from '../../modules/upeer-transport/src/UPeerTransportModule';

function hit(id: string, timestamp = 1): MessageSearchHit {
  return { chatUpeerId: 'peer', content: id, id, isMine: false, timestamp };
}

const result = (id: string, timestamp = 1): ChatSearchResult => ({ id, timestamp });

describe('searchExcerpt', () => {
  it('colapsa los espacios y recorta los extremos', () => {
    expect(searchExcerpt('  hola   mundo \n')).toBe('hola mundo');
  });

  it('recorta con puntos suspensivos al pasar del límite', () => {
    const excerpt = searchExcerpt('a'.repeat(200));
    expect(excerpt).toHaveLength(141);
    expect(excerpt.endsWith('…')).toBe(true);
  });

  it('llama archivo adjunto al contenido JSON', () => {
    expect(searchExcerpt('{"kind":"file"}')).toBe('Archivo adjunto');
    expect(searchExcerpt('{"kind":')).toBe('{"kind":');
  });
});

describe('toChatSearchResults', () => {
  it('se queda solo con lo que hace falta para saltar', () => {
    expect(toChatSearchResults([hit('m1', 1_700_000_000_000)])).toEqual([{ id: 'm1', timestamp: 1_700_000_000_000 }]);
  });

  it('conserva el orden que devuelve el proceso nativo', () => {
    expect(toChatSearchResults([hit('m2', 2), hit('m1', 1)]).map((item) => item.id)).toEqual(['m2', 'm1']);
  });

  it('no recorta la página recibida', () => {
    const hits = Array.from({ length: CHAT_SEARCH_PAGE_SIZE }, (_, index) => hit(`m${index}`, index));

    expect(toChatSearchResults(hits)).toHaveLength(CHAT_SEARCH_PAGE_SIZE);
  });
});

describe('appendChatSearchResults', () => {
  it('añade la página siguiente al final', () => {
    expect(appendChatSearchResults([result('m3')], [result('m2'), result('m1')]).map((item) => item.id)).toEqual(['m3', 'm2', 'm1']);
  });

  it('descarta duplicados entre páginas', () => {
    expect(appendChatSearchResults([result('m3'), result('m2')], [result('m2'), result('m1')]).map((item) => item.id)).toEqual(['m3', 'm2', 'm1']);
  });

  it('no muta la lista recibida', () => {
    const current = [result('m3')];
    appendChatSearchResults(current, [result('m2')]);

    expect(current.map((item) => item.id)).toEqual(['m3']);
  });
});

describe('searchPageHasMore', () => {
  it('pide otra página cuando la anterior vino llena', () => {
    expect(searchPageHasMore(CHAT_SEARCH_PAGE_SIZE)).toBe(true);
  });

  it('da la búsqueda por terminada con una página corta', () => {
    expect(searchPageHasMore(CHAT_SEARCH_PAGE_SIZE - 1)).toBe(false);
    expect(searchPageHasMore(0)).toBe(false);
  });
});

describe('resolveSearchStep', () => {
  it('avanza al siguiente resultado cargado', () => {
    expect(resolveSearchStep(0, 3, false, 1)).toEqual({ kind: 'jump', index: 1 });
    expect(resolveSearchStep(2, 3, false, -1)).toEqual({ kind: 'jump', index: 1 });
  });

  it('pide la página siguiente al llegar al final con más resultados', () => {
    expect(resolveSearchStep(2, 3, true, 1)).toEqual({ kind: 'load' });
  });

  it('vuelve al principio cuando ya no quedan páginas', () => {
    expect(resolveSearchStep(2, 3, false, 1)).toEqual({ kind: 'jump', index: 0 });
  });

  it('salta al final al retroceder desde el primero', () => {
    expect(resolveSearchStep(0, 3, false, -1)).toEqual({ kind: 'jump', index: 2 });
  });

  it('no envuelve hacia atrás mientras queden páginas sin cargar', () => {
    expect(resolveSearchStep(0, 3, true, -1)).toEqual({ kind: 'none' });
  });

  it('no hace nada sin resultados', () => {
    expect(resolveSearchStep(0, 0, true, 1)).toEqual({ kind: 'none' });
  });
});

describe('describeSearchPosition', () => {
  it('numera desde uno sobre el total', () => {
    expect(describeSearchPosition(0, 5)).toBe('1 de 5');
    expect(describeSearchPosition(4, 5)).toBe('5 de 5');
  });

  it('avisa de que quedan páginas sin cargar', () => {
    expect(describeSearchPosition(0, 50, true)).toBe('1 de 50+');
    expect(describeSearchPosition(49, 50, false)).toBe('50 de 50');
  });

  it('avisa cuando no hay coincidencias', () => {
    expect(describeSearchPosition(0, 0)).toBe('Sin resultados');
  });

  it('acota índices fuera de rango', () => {
    expect(describeSearchPosition(9, 3)).toBe('3 de 3');
    expect(describeSearchPosition(-2, 3)).toBe('1 de 3');
  });
});
