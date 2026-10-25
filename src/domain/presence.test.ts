import { describe, expect, it } from 'vitest';

import { activeAt, describeLastSeen, describePresence, isOnline, ONLINE_WINDOW_MS } from './presence';

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

describe('activeAt', () => {
  it('prefiere la actividad del usuario sobre la alcanzabilidad', () => {
    expect(activeAt(NOW - 5_000, NOW - 1_000)).toBe(NOW - 5_000);
  });

  it('cae a la alcanzabilidad cuando aún no hay actividad', () => {
    expect(activeAt(0, NOW - 1_000)).toBe(NOW - 1_000);
    expect(activeAt(undefined, NOW - 1_000)).toBe(NOW - 1_000);
  });

  it('devuelve null sin ningún dato', () => {
    expect(activeAt(undefined, undefined)).toBeNull();
    expect(activeAt(0, 0)).toBeNull();
  });
});

describe('describePresence', () => {
  const base = { now: NOW, onlineStatus: true, lastSeenEnabled: true };

  it('antepone En línea cuando el contacto está activo', () => {
    expect(describePresence({ ...base, lastSeen: NOW - 1_000 })).toBe('En línea');
  });

  it('no llama En línea a un nodo alcanzable sin actividad del usuario', () => {
    expect(describePresence({ ...base, lastActive: NOW - 5 * MINUTE, lastSeen: NOW - 1_000 })).toBe('Última vez hace 5 min');
  });

  it('llama En línea cuando la actividad del usuario es reciente', () => {
    expect(describePresence({ ...base, lastActive: NOW - 2_000, lastSeen: NOW - 3 * 60 * MINUTE })).toBe('En línea');
  });

  it('cae a última vez cuando la actividad es antigua', () => {
    expect(describePresence({ ...base, lastSeen: NOW - 5 * MINUTE })).toBe('Última vez hace 5 min');
  });

  it('oculta la última vez si la preferencia está apagada', () => {
    expect(describePresence({ ...base, lastSeenEnabled: false, lastSeen: NOW - 5 * MINUTE })).toBe('Desconectado');
  });

  it('oculta el En línea si la preferencia está apagada', () => {
    expect(describePresence({ ...base, onlineStatus: false, lastSeen: NOW - 1_000 })).toBe('Última vez hace un momento');
  });

  it('no inventa presencia sin datos', () => {
    expect(describePresence({ ...base, lastSeen: undefined })).toBe('Desconectado');
  });
});
