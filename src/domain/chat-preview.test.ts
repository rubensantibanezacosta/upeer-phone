import { describe, expect, it } from 'vitest';

import { describeChatPreview } from './chat-preview';

const NOW = new Date('2026-10-25T12:00:00').getTime();
const BASE = { now: NOW, shareActivity: true };

describe('describeChatPreview', () => {
  it('deja el texto tal cual cuando hay vista previa', () => {
    expect(describeChatPreview({ ...BASE, preview: 'Hola' })).toEqual({ text: 'Hola', accent: false, italic: false });
  });

  it('antepone la escritura a la vista previa', () => {
    expect(describeChatPreview({ ...BASE, preview: 'Hola', activity: 'typing' })).toEqual({ text: 'Escribiendo…', accent: true, italic: false });
  });

  it('distingue la grabación de la escritura', () => {
    expect(describeChatPreview({ ...BASE, preview: 'Hola', activity: 'recording' })).toEqual({
      text: 'Grabando audio…',
      accent: true,
      italic: false,
    });
  });

  it('vuelve a la vista previa cuando el aviso se para', () => {
    expect(describeChatPreview({ ...BASE, preview: 'Hola', activity: '' })).toEqual({ text: 'Hola', accent: false, italic: false });
  });

  it('mantiene la etiqueta pendiente en cursiva aunque haya escritura', () => {
    expect(describeChatPreview({ ...BASE, preview: 'Pendiente', pending: true, activity: 'recording' })).toEqual({
      text: 'Pendiente',
      accent: false,
      italic: true,
    });
  });

  it('cae a la presencia cuando no hay mensajes', () => {
    expect(describeChatPreview({ ...BASE, preview: '', lastActive: NOW - 1_000 }).text).toBe('En línea');
    expect(describeChatPreview({ ...BASE, preview: '', lastActive: NOW - 300_000 }).text).toBe('Última vez hace 5 min');
  });

  it('cae a «Sin mensajes» si compartir está apagado, haya o no actividad', () => {
    expect(describeChatPreview({ ...BASE, preview: '', lastActive: NOW - 300_000, shareActivity: false }).text).toBe('Sin mensajes');
    expect(describeChatPreview({ ...BASE, preview: '', shareActivity: false }).text).toBe('Sin mensajes');
  });

  it('no inventa presencia sin datos', () => {
    expect(describeChatPreview({ ...BASE, preview: '' }).text).toBe('Desconectado');
  });
});
