import { describe, expect, it } from 'vitest';

import {
  CHAT_STYLE_PRESETS,
  DEFAULT_CHAT_STYLE,
  chatStyleFor,
  getChatStylePreset,
  parseChatStyleMap,
  resolveChatStyleId,
  sanitizeChatStyleMap,
  setChatStyle,
} from './chat-style';

const chat = 'aa'.repeat(32);

describe('resolveChatStyleId', () => {
  it('keeps known presets', () => {
    expect(resolveChatStyleId('ocean')).toBe('ocean');
  });

  it('falls back to the default', () => {
    expect(resolveChatStyleId(undefined)).toBe(DEFAULT_CHAT_STYLE);
    expect(resolveChatStyleId('retirado')).toBe('default');
  });
});

describe('getChatStylePreset', () => {
  it('returns the preset', () => {
    expect(getChatStylePreset('paper').label).toBe('Papel');
  });

  it('returns the first preset when unknown', () => {
    expect(getChatStylePreset('nada')).toBe(CHAT_STYLE_PRESETS[0]);
  });

  it('leaves the default without tints', () => {
    expect(getChatStylePreset('default').tints).toHaveLength(0);
  });

  it('keeps every non default preset with at least one tint', () => {
    const empty = CHAT_STYLE_PRESETS.filter((preset) => preset.id !== 'default' && preset.tints.length === 0);

    expect(empty).toEqual([]);
  });
});

describe('sanitizeChatStyleMap', () => {
  it('drops the default and unknown values', () => {
    expect(sanitizeChatStyleMap({ [chat]: 'night', other: 'default', third: 'raro', n: 7 })).toEqual({ [chat]: 'night' });
  });

  it('rejects non objects', () => {
    expect(sanitizeChatStyleMap(null)).toEqual({});
    expect(sanitizeChatStyleMap(['night'])).toEqual({});
  });
});

describe('parseChatStyleMap', () => {
  it('reads stored JSON', () => {
    expect(parseChatStyleMap(JSON.stringify({ [chat]: 'forest' }))).toEqual({ [chat]: 'forest' });
  });

  it('survives broken payloads', () => {
    expect(parseChatStyleMap('{')).toEqual({});
    expect(parseChatStyleMap(null)).toEqual({});
  });
});

describe('chatStyleFor', () => {
  it('returns the stored style', () => {
    expect(chatStyleFor({ [chat]: 'sunset' }, chat)).toBe('sunset');
  });

  it('defaults to the theme background', () => {
    expect(chatStyleFor({}, chat)).toBe('default');
    expect(chatStyleFor({ [chat]: 'sunset' }, null)).toBe('default');
  });
});

describe('setChatStyle', () => {
  it('stores the chosen preset', () => {
    expect(setChatStyle({}, chat, 'ocean')).toEqual({ [chat]: 'ocean' });
  });

  it('removes the chat when the default is chosen', () => {
    expect(setChatStyle({ [chat]: 'ocean' }, chat, 'default')).toEqual({});
  });

  it('ignores unknown values', () => {
    expect(setChatStyle({ [chat]: 'ocean' }, chat, 'raro')).toEqual({});
  });
});
