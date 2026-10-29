import { describe, expect, it } from 'vitest';

import { previewIconKind, previewMessage } from './message-preview';

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

describe('previewIconKind', () => {
  it('reconoce la imagen por tipo y por extension', () => {
    expect(previewIconKind(JSON.stringify({ type: 'file', fileName: 'a.png' }))).toBe('image');
    expect(previewIconKind(JSON.stringify({ type: 'image', fileName: 'a.webp' }))).toBe('image');
    expect(previewIconKind(JSON.stringify({ type: 'file', fileName: 'sin-extension', mimeType: 'image/jpeg' }))).toBe('image');
  });

  it('reconoce el video', () => {
    expect(previewIconKind(JSON.stringify({ type: 'file', fileName: 'clip.mp4' }))).toBe('video');
    expect(previewIconKind(JSON.stringify({ type: 'file', fileName: 'x', mimeType: 'video/webm' }))).toBe('video');
  });

  it('reconoce el documento y la nota de voz', () => {
    expect(previewIconKind(JSON.stringify({ type: 'file', fileName: 'informe.pdf' }))).toBe('document');
    expect(previewIconKind(JSON.stringify({ type: 'file', fileName: 'voice_note_1.webm', isVoiceNote: true }))).toBe('voice');
  });

  it('reconoce el formato antiguo FILE_TRANSFER', () => {
    expect(previewIconKind('FILE_TRANSFER|abc|foto.jpg|1024|image/jpeg|')).toBe('image');
    expect(previewIconKind('FILE_TRANSFER|abc|clip.mkv|2048|video/x-matroska|')).toBe('video');
    expect(previewIconKind('FILE_TRANSFER|abc|informe.pdf|512|application/pdf|')).toBe('document');
  });

  it('no inventa icono para texto, avisos ni tarjetas', () => {
    expect(previewIconKind('hola')).toBeNull();
    expect(previewIconKind('')).toBeNull();
    expect(previewIconKind('__SYS__|Ivan creó el grupo')).toBeNull();
    expect(previewIconKind(JSON.stringify({ type: 'contact_card', contact: { upeerId: 'a'.repeat(64), name: 'Ivan' } }))).toBeNull();
  });
});
