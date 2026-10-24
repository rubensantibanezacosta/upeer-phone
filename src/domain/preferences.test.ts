import { describe, expect, it, vi } from 'vitest';

import { DEFAULT_PREFERENCES, parsePreferences } from './preferences';

describe('parsePreferences', () => {
  it('devuelve los valores por defecto cuando no hay nada guardado', () => {
    expect(parsePreferences(null)).toEqual(DEFAULT_PREFERENCES);
  });

  it('conserva los valores válidos', () => {
    const stored = JSON.stringify({ theme: 'light', fontSize: 'large', readReceipts: false, notificationSound: false });
    const parsed = parsePreferences(stored);
    expect(parsed.theme).toBe('light');
    expect(parsed.fontSize).toBe('large');
    expect(parsed.readReceipts).toBe(false);
    expect(parsed.notificationSound).toBe(false);
    expect(parsed.messageNotifications).toBe(true);
  });

  it('ignora valores desconocidos en los enums', () => {
    const parsed = parsePreferences(JSON.stringify({ theme: 'neon', fontSize: 'gigante' }));
    expect(parsed.theme).toBe(DEFAULT_PREFERENCES.theme);
    expect(parsed.fontSize).toBe(DEFAULT_PREFERENCES.fontSize);
  });

  it('cae en los valores por defecto si el JSON está corrupto', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    expect(parsePreferences('{no-json')).toEqual(DEFAULT_PREFERENCES);
    expect(warn).toHaveBeenCalled();
    warn.mockRestore();
  });
});
