import { describe, expect, it } from 'vitest';

import { attachmentLabel, attachmentSummary, describeMessageContent, formatFileSize, parseAttachment } from './attachment';

const file = JSON.stringify({
  type: 'file',
  transferId: 'a6cf7693-27aa-4d5e-9c1e-1f7f0e2b3c4d',
  fileId: 'a6cf7693-27aa-4d5e-9c1e-1f7f0e2b3c4d',
  fileName: 'informe.pdf',
  fileSize: 2048,
  mimeType: 'application/pdf',
});

describe('parseAttachment', () => {
  it('lee el sobre que emite escritorio', () => {
    expect(parseAttachment(file)).toEqual({
      kind: 'document',
      fileName: 'informe.pdf',
      fileSize: 2048,
      mimeType: 'application/pdf',
      transferId: 'a6cf7693-27aa-4d5e-9c1e-1f7f0e2b3c4d',
      caption: undefined,
    });
  });

  it('clasifica por mime y por extensión', () => {
    expect(parseAttachment(JSON.stringify({ type: 'file', mimeType: 'image/png' }))?.kind).toBe('image');
    expect(parseAttachment(JSON.stringify({ type: 'file', mimeType: 'video/mp4' }))?.kind).toBe('video');
    expect(parseAttachment(JSON.stringify({ type: 'file', mimeType: 'audio/ogg' }))?.kind).toBe('audio');
    expect(parseAttachment(JSON.stringify({ type: 'file', fileName: 'foto.JPG' }))?.kind).toBe('image');
    expect(parseAttachment(JSON.stringify({ type: 'file', fileName: 'clip.mov' }))?.kind).toBe('video');
    expect(parseAttachment(JSON.stringify({ type: 'file', fileName: 'nota.m4a' }))?.kind).toBe('audio');
    expect(parseAttachment(JSON.stringify({ type: 'file', fileName: 'datos.bin' }))?.kind).toBe('other');
  });

  it('marca la nota de voz por encima del mime', () => {
    const info = parseAttachment(JSON.stringify({ type: 'file', mimeType: 'audio/mp4', isVoiceNote: true }));
    expect(info?.kind).toBe('voice');
  });

  it('acepta el tipo image que usa el picker', () => {
    expect(parseAttachment(JSON.stringify({ type: 'image', fileId: 'x' }))?.kind).toBe('image');
  });

  it('devuelve null para texto, tarjetas y JSON roto', () => {
    expect(parseAttachment('hola')).toBeNull();
    expect(parseAttachment(JSON.stringify({ type: 'contact_card', contact: { upeerId: 'a'.repeat(64) } }))).toBeNull();
    expect(parseAttachment('{roto')).toBeNull();
    expect(parseAttachment(JSON.stringify({ type: 'file' }))).not.toBeNull();
  });

  it('recorta la etiqueta y cae a un nombre por defecto', () => {
    const info = parseAttachment(JSON.stringify({ type: 'file', fileName: '' }));
    expect(info?.fileName).toBe('Archivo');
    const long = parseAttachment(JSON.stringify({ type: 'file', fileName: 'x'.repeat(500) }));
    expect(long?.fileName.length).toBe(120);
  });

  it('lee el pie de foto cuando existe', () => {
    const info = parseAttachment(JSON.stringify({ type: 'file', fileName: 'a.png', caption: '  mira esto  ' }));
    expect(info?.caption).toBe('mira esto');
  });
});

describe('attachmentLabel', () => {
  it('no repite el nombre en foto, vídeo ni nota de voz', () => {
    expect(attachmentLabel({ kind: 'image', fileName: 'a.png', fileSize: 0, mimeType: '', transferId: '' })).toBe('Foto');
    expect(attachmentLabel({ kind: 'video', fileName: 'a.mp4', fileSize: 0, mimeType: '', transferId: '' })).toBe('Vídeo');
    expect(attachmentLabel({ kind: 'voice', fileName: 'a.m4a', fileSize: 0, mimeType: '', transferId: '' })).toBe('Nota de voz');
  });

  it('incluye el nombre en documentos y archivos', () => {
    expect(attachmentLabel({ kind: 'document', fileName: 'informe.pdf', fileSize: 0, mimeType: '', transferId: '' })).toBe('Documento: informe.pdf');
    expect(attachmentLabel({ kind: 'other', fileName: 'datos.bin', fileSize: 0, mimeType: '', transferId: '' })).toBe('Archivo: datos.bin');
  });
});

describe('attachmentSummary', () => {
  it('prefiere el pie de foto', () => {
    expect(attachmentSummary(JSON.stringify({ type: 'file', fileName: 'a.png', caption: 'mira esto' }))).toBe('mira esto');
  });

  it('devuelve null si no es un adjunto', () => {
    expect(attachmentSummary('hola')).toBeNull();
  });
});

describe('describeMessageContent', () => {
  it('sustituye el JSON crudo por una etiqueta legible', () => {
    expect(describeMessageContent(file)).toBe('Documento: informe.pdf');
  });

  it('deja pasar el texto normal', () => {
    expect(describeMessageContent('hola')).toBe('hola');
  });
});

describe('formatFileSize', () => {
  it('formatea bytes, kilobytes, megabytes y gigabytes', () => {
    expect(formatFileSize(0)).toBe('');
    expect(formatFileSize(-5)).toBe('');
    expect(formatFileSize(900)).toBe('900 B');
    expect(formatFileSize(2048)).toBe('2 kB');
    expect(formatFileSize(5 * 1024 * 1024)).toBe('5.0 MB');
    expect(formatFileSize(3 * 1024 * 1024 * 1024)).toBe('3.0 GB');
  });
});
