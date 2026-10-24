import { describe, expect, it } from 'vitest';

import { describeSearchPosition, searchChatMessages, searchExcerpt } from './chat-search';
import type { ProtocolMessage } from '../../modules/upeer-transport/src/UPeerTransport.types';

function message(id: string, content: string, isMine = false, timestamp = 1): ProtocolMessage {
  return { content, id, isMine, status: 'read', timestamp, upeerId: 'peer' };
}

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

describe('searchChatMessages', () => {
  it('devuelve vacío sin consulta', () => {
    expect(searchChatMessages([message('1', 'hola')], '   ')).toEqual([]);
  });

  it('ignora mayúsculas y acentos', () => {
    const result = searchChatMessages([message('1', 'Mañana temprano')], 'manana');
    expect(result.map((item) => item.id)).toEqual(['1']);
  });

  it('ordena de más reciente a más antiguo', () => {
    const result = searchChatMessages([message('1', 'hola'), message('2', 'hola otra vez'), message('3', 'adiós')], 'hola');
    expect(result.map((item) => item.id)).toEqual(['2', '1']);
  });

  it('conserva autor, hora y extracto', () => {
    const [result] = searchChatMessages([message('1', 'nos vemos el lunes', true, 1700000000000)], 'lunes');
    expect(result).toEqual({ excerpt: 'nos vemos el lunes', id: '1', isMine: true, timestamp: 1700000000000 });
  });

  it('se queda con los 50 más recientes', () => {
    const many = Array.from({ length: 80 }, (_, index) => message(`m${index}`, 'hola'));
    const result = searchChatMessages(many, 'hola');
    expect(result).toHaveLength(50);
    expect(result[0].id).toBe('m79');
  });
});

describe('describeSearchPosition', () => {
  it('numera desde uno sobre el total', () => {
    expect(describeSearchPosition(0, 5)).toBe('1 de 5');
    expect(describeSearchPosition(4, 5)).toBe('5 de 5');
  });

  it('avisa cuando no hay coincidencias', () => {
    expect(describeSearchPosition(0, 0)).toBe('Sin resultados');
  });

  it('acota índices fuera de rango', () => {
    expect(describeSearchPosition(9, 3)).toBe('3 de 3');
    expect(describeSearchPosition(-2, 3)).toBe('1 de 3');
  });
});
