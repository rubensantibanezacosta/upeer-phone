import { describe, expect, it } from 'vitest';

import { FORWARD_EXCERPT_LENGTH, forwardExcerpt, forwardResultMessage } from './forward';

describe('forwardExcerpt', () => {
  it('colapsa los espacios y recorta los extremos', () => {
    expect(forwardExcerpt('  hola   mundo \n')).toBe('hola mundo');
  });

  it('recorta con puntos suspensivos al pasar del límite', () => {
    const excerpt = forwardExcerpt('a'.repeat(FORWARD_EXCERPT_LENGTH + 10));
    expect(excerpt).toHaveLength(FORWARD_EXCERPT_LENGTH + 1);
    expect(excerpt.endsWith('…')).toBe(true);
  });

  it('no toca un texto dentro del límite', () => {
    expect(forwardExcerpt('corto')).toBe('corto');
  });
});

describe('forwardResultMessage', () => {
  it('singular y plural', () => {
    expect(forwardResultMessage(1, 0)).toBe('Reenviado a 1 contacto');
    expect(forwardResultMessage(3, 0)).toBe('Reenviado a 3 contactos');
  });

  it('informa de los fallos parciales', () => {
    expect(forwardResultMessage(2, 1)).toBe('Reenviado a 2; falló 1');
  });

  it('avisa cuando no salió ninguno', () => {
    expect(forwardResultMessage(0, 2)).toBe('No se pudo reenviar el mensaje');
    expect(forwardResultMessage(0, 0)).toBe('Elige un destinatario');
  });
});
