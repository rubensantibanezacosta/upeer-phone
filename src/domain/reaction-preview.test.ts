import { describe, expect, it } from 'vitest';

import { describeReactedContent, reactionPreview } from './reaction-preview';

const preview = (overrides: Partial<Parameters<typeof reactionPreview>[0]> = {}) => reactionPreview({
  emoji: '🤙',
  message: 'asfjasklfjaslkfj',
  actorName: 'Ivan',
  actorIsMe: false,
  ...overrides,
});

describe('describeReactedContent', () => {
  it('cita el texto del mensaje', () => {
    expect(describeReactedContent('asfjasklfjaslkfj')).toBe('al mensaje "asfjasklfjaslkfj"');
  });

  it('nombra la imagen por su fichero', () => {
    const image = JSON.stringify({ type: 'file', fileName: 'asfasf.png', mimeType: 'image/png' });
    expect(describeReactedContent(image)).toBe('a la imagen "asfasf.png"');
  });

  it('nombra el vídeo, el archivo y la nota de voz', () => {
    expect(describeReactedContent(JSON.stringify({ type: 'file', fileName: 'clip.mp4', mimeType: 'video/mp4' }))).toBe('al vídeo "clip.mp4"');
    expect(describeReactedContent(JSON.stringify({ type: 'file', fileName: 'informe.pdf', mimeType: 'application/pdf' }))).toBe('al archivo "informe.pdf"');
    expect(describeReactedContent(JSON.stringify({ type: 'file', fileName: 'nota.webm', isVoiceNote: true }))).toBe('a la nota de voz');
  });

  it('reconoce la imagen por la extensión cuando falta el mime', () => {
    expect(describeReactedContent(JSON.stringify({ type: 'file', fileName: 'foto.JPG' }))).toBe('a la imagen "foto.JPG"');
  });

  it('describe la tarjeta de contacto sin repetir su etiqueta', () => {
    const card = JSON.stringify({ type: 'contact_card', contact: { name: 'Ana', upeerId: 'b'.repeat(64) } });
    expect(describeReactedContent(card)).toBe('a una tarjeta de contacto');
  });

  it('describe el aviso de sistema ya humanizado', () => {
    expect(describeReactedContent('__SYS__|Ivan te añadió al grupo')).toBe('al aviso "Ivan te añadió al grupo"');
  });

  it('recorta los textos largos', () => {
    expect(describeReactedContent('a'.repeat(200))).toBe(`al mensaje "${'a'.repeat(64)}…"`);
  });

  it('no describe nada cuando el mensaje está vacío', () => {
    expect(describeReactedContent('')).toBe('');
  });
});

describe('reactionPreview', () => {
  it('cuenta quién reaccionó, con qué y a qué', () => {
    expect(preview()).toBe('Ivan reaccionó con 🤙 al mensaje "asfjasklfjaslkfj"');
  });

  it('habla en segunda persona cuando la reacción es mía', () => {
    expect(preview({ actorIsMe: true, actorName: '' })).toBe('Reaccionaste con 🤙 al mensaje "asfjasklfjaslkfj"');
  });

  it('no inventa un nombre cuando el actor es desconocido', () => {
    expect(preview({ actorName: '' })).toBe('Alguien reaccionó con 🤙 al mensaje "asfjasklfjaslkfj"');
  });

  it('omite el complemento cuando el mensaje reaccionado está vacío', () => {
    expect(preview({ message: '' })).toBe('Ivan reaccionó con 🤙');
  });

  it('devuelve null sin reacción', () => {
    expect(reactionPreview(undefined)).toBeNull();
    expect(reactionPreview(null)).toBeNull();
    expect(reactionPreview({ emoji: '', message: '' })).toBeNull();
  });

  it('devuelve solo el actor cuando no hay emoji', () => {
    expect(preview({ emoji: ' ' })).toBe('Ivan reaccionó al mensaje "asfjasklfjaslkfj"');
  });
});
