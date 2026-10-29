import { describe, expect, it } from 'vitest';

import { describeLastSeen, describePresence, isOnline, ONLINE_WINDOW_MS } from './presence';

const NOW = new Date('2026-10-25T12:00:00').getTime();
const MINUTE = 60_000;

describe('isOnline', () => {
  it('considera conectado lo visto dentro de la ventana', () => {
    expect(isOnline(NOW - 30_000, NOW)).toBe(true);
  });

  it('deja de estar conectado al superar la ventana', () => {
    expect(isOnline(NOW - ONLINE_WINDOW_MS - 1, NOW)).toBe(false);
  });

  it('trata la ausencia de datos como desconectado', () => {
    expect(isOnline(undefined, NOW)).toBe(false);
    expect(isOnline(0, NOW)).toBe(false);
  });
});

describe('describeLastSeen', () => {
  it('resume los primeros minutos', () => {
    expect(describeLastSeen(NOW - 10_000, NOW)).toBe('hace un momento');
    expect(describeLastSeen(NOW - 5 * MINUTE, NOW)).toBe('hace 5 min');
  });

  it('usa la hora del día cuando es hoy', () => {
    expect(describeLastSeen(NOW - 3 * 60 * MINUTE, NOW)).toBe('hoy a las 09:00');
  });

  it('incluye día y mes cuando es otro día', () => {
    expect(describeLastSeen(new Date('2026-10-21T18:30:00').getTime(), NOW)).toBe('el 21 oct a las 18:30');
  });

  it('devuelve null sin datos', () => {
    expect(describeLastSeen(null, NOW)).toBeNull();
  });
});

describe('describePresence', () => {
  const base = { now: NOW, shareActivity: true };

  it('llama En línea cuando la actividad anunciada es reciente', () => {
    expect(describePresence({ ...base, lastActive: NOW - 2_000 })).toBe('En línea');
  });

  it('cae a última vez cuando la actividad es antigua', () => {
    expect(describePresence({ ...base, lastActive: NOW - 5 * MINUTE })).toBe('Última vez hace 5 min');
  });

  it('no anuncia conexión si el contacto no comparte su actividad', () => {
    expect(describePresence({ ...base, lastActive: undefined })).toBe('Desconectado');
    expect(describePresence({ ...base, lastActive: 0 })).toBe('Desconectado');
  });

  it('no muestra nada de nadie si compartir está apagado', () => {
    expect(describePresence({ ...base, shareActivity: false, lastActive: NOW - 2_000 })).toBeNull();
    expect(describePresence({ ...base, shareActivity: false, lastActive: NOW - 5 * MINUTE })).toBeNull();
    expect(describePresence({ ...base, shareActivity: false, lastActive: undefined })).toBeNull();
  });
});
