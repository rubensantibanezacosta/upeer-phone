import { describe, expect, it } from 'vitest';

import { EMOJI_GROUPS, emojisForGroup, pushRecentEmoji, RECENT_EMOJI_LIMIT, searchEmojis } from './emoji-catalog';

describe('catalogo de emojis', () => {
  it('conserva las ocho categorias del escritorio', () => {
    expect(EMOJI_GROUPS.map((group) => group.id)).toEqual(['smileys', 'people', 'food', 'travel', 'activities', 'objects', 'symbols', 'flags']);
    expect(EMOJI_GROUPS.every((group) => group.emojis.length > 0)).toBe(true);
  });

  it('devuelve la lista de un grupo y vacio si no existe', () => {
    expect(emojisForGroup('smileys')).toContain('😀');
    expect(emojisForGroup('inexistente')).toEqual([]);
  });

  it('busca por nombre de categoria y devuelve los emojis de esa categoria', () => {
    const results = searchEmojis('comida');
    expect(results.length).toBe(emojisForGroup('food').length);
    expect(results).toContain('🍕');
  });

  it('busca un emoji literal dentro de todo el catalogo', () => {
    expect(searchEmojis('🍕')).toContain('🍕');
  });

  it('no devuelve nada con la consulta vacia ni con una busqueda sin resultados', () => {
    expect(searchEmojis('   ')).toEqual([]);
    expect(searchEmojis('zzzz')).toEqual([]);
  });

  it('mueve el emoji al principio de los recientes sin duplicarlo y recorta', () => {
    expect(pushRecentEmoji(['👍', '❤️'], '❤️')).toEqual(['❤️', '👍']);
    const full = Array.from({ length: RECENT_EMOJI_LIMIT }, (_, index) => `e${index}`);
    expect(pushRecentEmoji(full, 'nuevo')).toHaveLength(RECENT_EMOJI_LIMIT);
    expect(pushRecentEmoji(full, 'nuevo')[0]).toBe('nuevo');
  });
});
