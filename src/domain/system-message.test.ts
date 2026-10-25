import { describe, expect, it } from 'vitest';

import { isSystemMessage, systemMessageText } from './system-message';

describe('systemMessageText', () => {
  it('quita el prefijo de los avisos de grupo', () => {
    expect(systemMessageText('__SYS__|Rubén te añadió al grupo')).toBe('Rubén te añadió al grupo');
  });

  it('recorta la marca de tiempo de los eventos de llamada', () => {
    expect(systemMessageText('__SYS__|Llamada de voz · 2:31 · 1730000000000')).toBe('Llamada de voz · 2:31');
    expect(systemMessageText('__SYS__|Videollamada · 00:07 · 1730000000000')).toBe('Videollamada · 00:07');
  });

  it('mantiene los resultados sin duración tal cual', () => {
    expect(systemMessageText('__SYS__|Llamada de voz perdida')).toBe('Llamada de voz perdida');
    expect(systemMessageText('__SYS__|Videollamada rechazada')).toBe('Videollamada rechazada');
  });

  it('no confunde un texto con separadores con un evento de llamada', () => {
    expect(systemMessageText('__SYS__|a · b · c')).toBe('a · b · c');
  });

  it('devuelve null cuando no es un aviso de sistema', () => {
    expect(systemMessageText('hola')).toBeNull();
    expect(systemMessageText('__SYS__|')).toBeNull();
    expect(systemMessageText('__SYS__|   ')).toBeNull();
  });

  it('detecta el prefijo', () => {
    expect(isSystemMessage('__SYS__|algo')).toBe(true);
    expect(isSystemMessage('algo __SYS__|')).toBe(false);
  });
});
