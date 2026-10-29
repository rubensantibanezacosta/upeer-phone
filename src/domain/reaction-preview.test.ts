import { describe, expect, it } from 'vitest';

import { reactionPreview } from './reaction-preview';

describe('reactionPreview', () => {
  it('antepone el emoji al mensaje reaccionado', () => {
    expect(reactionPreview({ emoji: '👍', message: 'hola' })).toBe('👍 hola');
  });

  it('usa el resumen del mensaje reaccionado cuando es un adjunto', () => {
    const file = JSON.stringify({ kind: 'file', type: 'file', fileName: 'informe.pdf', mimeType: 'application/pdf', fileId: 'f', url: 'u', size: 1 });
    expect(reactionPreview({ emoji: '🔥', message: file })).toContain('informe.pdf');
  });

  it('devuelve solo el emoji cuando el mensaje reaccionado está vacío', () => {
    expect(reactionPreview({ emoji: '❤️', message: '' })).toBe('❤️');
  });

  it('devuelve null sin reacción', () => {
    expect(reactionPreview(undefined)).toBeNull();
    expect(reactionPreview(null)).toBeNull();
    expect(reactionPreview({ emoji: '', message: '' })).toBeNull();
  });

  it('devuelve solo el texto cuando no hay emoji', () => {
    expect(reactionPreview({ emoji: ' ', message: 'hola' })).toBe('hola');
  });
});
