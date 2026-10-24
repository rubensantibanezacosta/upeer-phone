import { describe, expect, it } from 'vitest';

import { describeChatPreview } from './chat-preview';

const NOW = new Date('2026-10-25T12:00:00').getTime();
const BASE = { now: NOW, onlineStatus: true, lastSeenEnabled: true };

describe('describeChatPreview', () => {
  it('deja el texto tal cual cuando hay vista previa', () => {
    expect(describeChatPreview({ ...BASE, preview: 'Hola' })).toEqual({ text: 'Hola', accent: false, italic: false });
  });

  it('antepone la escritura a la vista previa', () => {
    expect(describeChatPreview({ ...BASE, preview: 'Hola', typing: true })).toEqual({ text: 'Escribiendo…', accent: true, italic: false });
  });

  it('mantiene la etiqueta pendiente en cursiva aunque haya escritura', () => {
    expect(describeChatPreview({ ...BASE, preview: 'Pendiente', pending: true, typing: true })).toEqual({
      text: 'Pendiente',
      accent: false,
      italic: true,
    });
  });

  it('cae a la presencia cuando no hay mensajes', () => {
    expect(describeChatPreview({ ...BASE, preview: '', lastSeen: NOW - 1_000 }).text).toBe('En línea');
    expect(describeChatPreview({ ...BASE, preview: '', lastSeen: NOW - 300_000 }).text).toBe('Última vez hace 5 min');
  });

  it('respeta la preferencia de última vez', () => {
    expect(describeChatPreview({ ...BASE, preview: '', lastSeen: NOW - 300_000, lastSeenEnabled: false }).text).toBe('Desconectado');
  });

  it('no inventa presencia sin datos', () => {
    expect(describeChatPreview({ ...BASE, preview: '' }).text).toBe('Desconectado');
  });
});
