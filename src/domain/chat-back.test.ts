import { describe, expect, it } from 'vitest';

import { resolveChatBack, type ChatBackState } from './chat-back';

const NONE: ChatBackState = {
  menuOpen: false,
  searchActive: false,
  sheetOpen: false,
  cardOpen: false,
  groupOpen: false,
  forwarding: false,
  composing: false,
};

describe('resolveChatBack', () => {
  it('sale de la conversación cuando no hay nada abierto', () => {
    expect(resolveChatBack(NONE)).toBe('leave');
  });

  it('cierra el menú antes que nada', () => {
    expect(resolveChatBack({ ...NONE, menuOpen: true, searchActive: true, sheetOpen: true })).toBe('close-menu');
  });

  it('cierra la búsqueda antes que la hoja de acciones', () => {
    expect(resolveChatBack({ ...NONE, searchActive: true, sheetOpen: true })).toBe('close-search');
  });

  it('cierra la hoja del mensaje antes que la tarjeta de contacto', () => {
    expect(resolveChatBack({ ...NONE, sheetOpen: true, cardOpen: true })).toBe('close-sheet');
  });

  it('cierra la tarjeta de contacto antes que el reenvío', () => {
    expect(resolveChatBack({ ...NONE, cardOpen: true, forwarding: true })).toBe('close-card');
  });

  it('cierra la ficha de grupo antes que el reenvío', () => {
    expect(resolveChatBack({ ...NONE, groupOpen: true, forwarding: true })).toBe('close-group');
  });

  it('cancela el reenvío antes que la composición', () => {
    expect(resolveChatBack({ ...NONE, forwarding: true, composing: true })).toBe('cancel-forwarding');
  });

  it('cancela la edición o la respuesta al final', () => {
    expect(resolveChatBack({ ...NONE, composing: true })).toBe('cancel-composing');
  });
});
