import { describe, expect, it } from 'vitest';

import { presenceText } from './contact-labels';

describe('presenceText', () => {
  it('deja pasar la etiqueta calculada con datos de presencia', () => {
    expect(presenceText('Última vez hace 2 min', 'offline')).toBe('Última vez hace 2 min');
    expect(presenceText('En línea', 'connected')).toBe('En línea');
    expect(presenceText('Desconectado', 'connected')).toBe('Desconectado');
  });

  it('no afirma conexión cuando no hay ningún dato', () => {
    expect(presenceText(null, 'connected')).toBe('Desconectado');
    expect(presenceText(undefined, 'connected')).toBe('Desconectado');
    expect(presenceText('', 'connected')).toBe('Desconectado');
    expect(presenceText(null, 'offline')).toBe('Desconectado');
  });

  it('no oculta los estados que no son presencia', () => {
    expect(presenceText(null, 'blocked')).toBe('Bloqueado');
    expect(presenceText(null, 'pending')).toBe('Pendiente');
    expect(presenceText(null, 'incoming')).toBe('Solicitud de contacto');
  });
});
