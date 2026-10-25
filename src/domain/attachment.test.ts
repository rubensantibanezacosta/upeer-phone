import { describe, expect, it } from 'vitest';

import {
  attachmentDetail,
  attachmentLabel,
  describeMessageContent,
  fileExtension,
  formatFileSize,
  parseAttachment,
  parseAttachmentMessage,
} from './attachment';

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
      thumbnail: undefined,
    });
  });

  it('clasifica igual que escritorio: imagen, vídeo y el resto como documento', () => {
    expect(parseAttachment(JSON.stringify({ type: 'file', mimeType: 'image/png' }))?.kind).toBe('image');
    expect(parseAttachment(JSON.stringify({ type: 'file', mimeType: 'video/mp4' }))?.kind).toBe('video');
    expect(parseAttachment(JSON.stringify({ type: 'file', mimeType: 'audio/ogg' }))?.kind).toBe('document');
    expect(parseAttachment(JSON.stringify({ type: 'file', fileName: 'foto.JPG' }))?.kind).toBe('image');
    expect(parseAttachment(JSON.stringify({ type: 'file', fileName: 'clip.mov' }))?.kind).toBe('video');
    expect(parseAttachment(JSON.stringify({ type: 'file', fileName: 'datos.bin' }))?.kind).toBe('document');
  });

  it('marca la nota de voz por encima del mime', () => {
    const info = parseAttachment(JSON.stringify({ type: 'file', mimeType: 'video/webm', fileName: 'voice_note_1.webm', isVoiceNote: true }));
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

  it('recorta el nombre y cae a un nombre por defecto', () => {
    expect(parseAttachment(JSON.stringify({ type: 'file', fileName: '' }))?.fileName).toBe('Archivo');
    expect(parseAttachment(JSON.stringify({ type: 'file', fileName: 'x'.repeat(500) }))?.fileName.length).toBe(400);
  });

  it('lee el pie de foto cuando existe', () => {
    expect(parseAttachment(JSON.stringify({ type: 'file', fileName: 'a.png', caption: '  mira esto  ' }))?.caption).toBe('mira esto');
  });

  it('solo acepta miniaturas que sean imágenes en linea', () => {
    expect(parseAttachment(JSON.stringify({ type: 'file', thumbnail: 'data:image/png;base64,AAA' }))?.thumbnail).toBe('data:image/png;base64,AAA');
    expect(parseAttachment(JSON.stringify({ type: 'file', thumbnail: '/tmp/foto.png' }))?.thumbnail).toBeUndefined();
  });
});

describe('parseAttachmentMessage', () => {
  it('entiende el formato antiguo FILE_TRANSFER', () => {
    const info = parseAttachmentMessage('FILE_TRANSFER|abc|informe.pdf|2048|application/pdf|hash');
    expect(info?.fileName).toBe('informe.pdf');
    expect(info?.fileSize).toBe(2048);
    expect(info?.kind).toBe('document');
  });

  it('ignora el formato antiguo truncado', () => {
    expect(parseAttachmentMessage('FILE_TRANSFER|abc|informe.pdf')).toBeNull();
  });
});

describe('attachmentLabel', () => {
  it('llama Nota de voz a la nota de voz y usa el nombre en el resto', () => {
    expect(attachmentLabel({ kind: 'voice', fileName: 'v.webm', fileSize: 0, mimeType: '', transferId: '' })).toBe('Nota de voz');
    expect(attachmentLabel({ kind: 'document', fileName: 'informe.pdf', fileSize: 0, mimeType: '', transferId: '' })).toBe('informe.pdf');
    expect(attachmentLabel({ kind: 'image', fileName: 'a.png', fileSize: 0, mimeType: '', transferId: '' })).toBe('a.png');
  });
});

describe('attachmentDetail', () => {
  it('junta extension y tamano como la ficha de escritorio', () => {
    expect(attachmentDetail({ kind: 'document', fileName: 'informe.pdf', fileSize: 2048, mimeType: '', transferId: '' })).toBe('PDF · 2 kB');
    expect(attachmentDetail({ kind: 'document', fileName: 'sinext', fileSize: 0, mimeType: '', transferId: '' })).toBe('');
  });
});

describe('describeMessageContent', () => {
  it('muestra el nombre del archivo y no el JSON crudo', () => {
    expect(describeMessageContent(file)).toBe('informe.pdf');
  });

  it('resume la nota de voz', () => {
    expect(describeMessageContent(JSON.stringify({ type: 'file', fileName: 'voice_note_1.webm', isVoiceNote: true }))).toBe('Nota de voz');
  });

  it('prefiere el pie de foto', () => {
    expect(describeMessageContent(JSON.stringify({ type: 'file', fileName: 'a.png', caption: 'mira esto' }))).toBe('mira esto');
  });

  it('deja pasar el texto normal', () => {
    expect(describeMessageContent('hola')).toBe('hola');
  });
});

describe('fileExtension', () => {
  it('devuelve la extension en mayusculas', () => {
    expect(fileExtension('informe.pdf')).toBe('PDF');
    expect(fileExtension('sin-extension')).toBe('');
    expect(fileExtension('raro.')).toBe('');
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
