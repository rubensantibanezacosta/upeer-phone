import { describe, expect, it } from 'vitest';

import { presenceText } from './contact-labels';

describe('presenceText', () => {
  it('deja pasar la etiqueta calculada con datos de presencia', () => {
    expect(presenceText('Última vez hace 2 min', 'offline')).toBe('Última vez hace 2 min');
    expect(presenceText('En línea', 'connected')).toBe('En línea');
    expect(presenceText('Desconectado', 'connected')).toBe('Desconectado');
  });

  it('no dice nada de la conexión si no se comparte actividad', () => {
    expect(presenceText(null, 'connected')).toBe('');
    expect(presenceText(undefined, 'connected')).toBe('');
    expect(presenceText('', 'connected')).toBe('');
    expect(presenceText(null, 'offline')).toBe('');
  });

  it('no oculta los estados que no son presencia', () => {
    expect(presenceText(null, 'blocked')).toBe('Bloqueado');
    expect(presenceText(null, 'pending')).toBe('Pendiente');
    expect(presenceText(null, 'incoming')).toBe('Solicitud de contacto');
  });
});
