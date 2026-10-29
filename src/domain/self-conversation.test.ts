import { describe, expect, it } from 'vitest';

import { canActOnPeer, isSelfConversation } from './self-conversation';

const ME = 'da4163f3528710db1adeca2211274d217728845044c70b912759e856493d1834';
const OTHER = 'a60cc35ee3d4e106f7563d9ba4c2deaad4dc0596ded6acdab53585fba354948b';

describe('isSelfConversation', () => {
  it('reconoce mi propio chat sin importar el formato', () => {
    expect(isSelfConversation(ME, ME)).toBe(true);
    expect(isSelfConversation(ME.toUpperCase(), ME)).toBe(true);
    expect(isSelfConversation(ME, ME.toUpperCase())).toBe(true);
  });

  it('no confunde el chat de un contacto con el mio', () => {
    expect(isSelfConversation(OTHER, ME)).toBe(false);
  });

  it('no adivina cuando falta alguno de los dos identificadores', () => {
    expect(isSelfConversation(undefined, ME)).toBe(false);
    expect(isSelfConversation(ME, '')).toBe(false);
    expect(isSelfConversation('', '')).toBe(false);
  });
});

describe('canActOnPeer', () => {
  it('permite todo contra otro contacto', () => {
    for (const action of ['call', 'video', 'block', 'delete', 'favorite', 'clear', 'close', 'mute', 'export'] as const) {
      expect(canActOnPeer(action, false)).toBe(true);
    }
  });

  it('bloquea lo que no tiene sentido contra uno mismo', () => {
    for (const action of ['call', 'video', 'block', 'delete', 'favorite'] as const) {
      expect(canActOnPeer(action, true)).toBe(false);
    }
  });

  it('deja intacto lo que si puedo hacer en mi propio chat', () => {
    for (const action of ['clear', 'close', 'mute', 'export'] as const) {
      expect(canActOnPeer(action, true)).toBe(true);
    }
  });
});
