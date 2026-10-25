import { describe, expect, it } from 'vitest';

import { previewMessage } from './message-preview';

describe('previewMessage', () => {
  it('resume el evento de llamada sin el prefijo', () => {
    expect(previewMessage('__SYS__|Videollamada · 1:02 · 1730000000000')).toBe('Videollamada · 1:02');
  });

  it('resume los avisos de sistema', () => {
    expect(previewMessage('__SYS__|Ivan creó el grupo')).toBe('Ivan creó el grupo');
  });

  it('nombra el archivo en lugar del JSON', () => {
    expect(previewMessage(JSON.stringify({ type: 'file', fileName: 'informe.pdf' }))).toBe('informe.pdf');
  });

  it('resume la nota de voz', () => {
    expect(previewMessage(JSON.stringify({ type: 'file', fileName: 'voice_note_1.webm', isVoiceNote: true }))).toBe('Nota de voz');
  });

  it('resume la tarjeta de contacto', () => {
    const card = JSON.stringify({ type: 'contact_card', contact: { upeerId: 'a'.repeat(64), name: 'Ivan' } });
    expect(previewMessage(card)).toBe('Tarjeta de contacto: Ivan');
  });

  it('deja pasar el texto normal y el vacio', () => {
    expect(previewMessage('hola')).toBe('hola');
    expect(previewMessage('')).toBe('');
  });
});
