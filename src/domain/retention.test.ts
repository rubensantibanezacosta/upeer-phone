import { describe, expect, it } from 'vitest';

import {
  DEFAULT_RETENTION,
  RETENTION_PRESETS,
  computeExpiresAt,
  dropExpired,
  isExpired,
  parseRetentionMap,
  resolveRetentionId,
  retentionFor,
  retentionLabel,
  retentionMs,
  retentionNotice,
  sanitizeRetentionMap,
  setRetention,
} from './retention';

const HOUR = 60 * 60 * 1000;
const chat = 'aa'.repeat(32);

describe('resolveRetentionId', () => {
  it('keeps known presets', () => {
    expect(resolveRetentionId('24h')).toBe('24h');
  });

  it('falls back to off', () => {
    expect(resolveRetentionId(undefined)).toBe(DEFAULT_RETENTION);
    expect(resolveRetentionId('1h')).toBe('off');
  });
});

describe('retentionMs', () => {
  it('maps presets to milliseconds', () => {
    expect(retentionMs('off')).toBe(0);
    expect(retentionMs('24h')).toBe(24 * HOUR);
    expect(retentionMs('7d')).toBe(7 * 24 * HOUR);
  });
});

describe('retentionLabel y retentionNotice', () => {
  it('labels every preset', () => {
    expect(RETENTION_PRESETS.map((preset) => retentionLabel(preset.id))).toEqual(['Desactivados', '24 h', '7 días', '90 días']);
  });

  it('only notifies when the preset is enabled', () => {
    expect(retentionNotice('off')).toBeNull();
    expect(retentionNotice('24h')).toBe('Los mensajes nuevos se borran a los 24 h.');
  });
});

describe('sanitizeRetentionMap', () => {
  it('drops disabled and unknown entries', () => {
    expect(sanitizeRetentionMap({ [chat]: '7d', other: 'off', third: 'raro', empty: 3 })).toEqual({ [chat]: '7d' });
  });

  it('rejects non objects', () => {
    expect(sanitizeRetentionMap(null)).toEqual({});
    expect(sanitizeRetentionMap(['7d'])).toEqual({});
  });
});

describe('parseRetentionMap', () => {
  it('reads stored JSON', () => {
    expect(parseRetentionMap(JSON.stringify({ [chat]: '90d' }))).toEqual({ [chat]: '90d' });
  });

  it('survives broken payloads', () => {
    expect(parseRetentionMap('{')).toEqual({});
    expect(parseRetentionMap(null)).toEqual({});
  });
});

describe('retentionFor', () => {
  it('returns the stored preset', () => {
    expect(retentionFor({ [chat]: '7d' }, chat)).toBe('7d');
  });

  it('defaults to off', () => {
    expect(retentionFor({}, chat)).toBe('off');
    expect(retentionFor({ [chat]: '7d' }, null)).toBe('off');
  });
});

describe('setRetention', () => {
  it('stores enabled presets', () => {
    expect(setRetention({}, chat, '24h')).toEqual({ [chat]: '24h' });
  });

  it('removes the chat when disabled', () => {
    expect(setRetention({ [chat]: '24h' }, chat, 'off')).toEqual({});
  });

  it('ignores unknown values', () => {
    expect(setRetention({ [chat]: '24h' }, chat, 'raro')).toEqual({});
  });
});

describe('computeExpiresAt', () => {
  it('adds the window to the timestamp', () => {
    expect(computeExpiresAt(1000, 500)).toBe(1500);
  });

  it('returns null without a window', () => {
    expect(computeExpiresAt(1000, 0)).toBeNull();
    expect(computeExpiresAt(Number.NaN, 500)).toBeNull();
  });
});

describe('isExpired', () => {
  it('ignores missing stamps', () => {
    expect(isExpired(undefined, 10)).toBe(false);
    expect(isExpired(0, 10)).toBe(false);
  });

  it('compares against now', () => {
    expect(isExpired(10, 10)).toBe(true);
    expect(isExpired(11, 10)).toBe(false);
  });
});

describe('dropExpired', () => {
  it('removes only the expired messages', () => {
    const kept = dropExpired([{ id: 'old', expiresAt: 5 }, { id: 'live', expiresAt: 50 }, { id: 'forever' }], 10);

    expect(kept.map((message) => message.id)).toEqual(['live', 'forever']);
  });
});
