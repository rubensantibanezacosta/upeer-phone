import { beforeEach, describe, expect, it, vi } from 'vitest';

import { attachmentOpenMessage, openAttachment, OWN_DEVICE_NOTICE, planAttachmentOpen } from './attachment-open';

const native = vi.hoisted(() => ({
  exists: true,
  sharingAvailable: true,
  shareError: null as Error | null,
  shares: [] as { uri: string; options?: { mimeType?: string; dialogTitle?: string } }[],
}));

vi.mock('expo-file-system', () => ({
  File: class {
    readonly uri: string;

    constructor(uri: string) {
      this.uri = uri;
    }

    get exists() {
      return native.exists;
    }
  },
}));

vi.mock('expo-sharing', () => ({
  isAvailableAsync: async () => native.sharingAvailable,
  shareAsync: async (uri: string, options?: { mimeType?: string; dialogTitle?: string }) => {
    if (native.shareError) throw native.shareError;
    native.shares.push({ uri, options });
  },
}));

const info = (extra: Record<string, unknown> = {}) => ({
  kind: 'document' as const,
  fileName: 'informe.pdf',
  fileSize: 2048,
  mimeType: 'application/pdf',
  transferId: 'f1',
  localPath: '/data/app/attachments/direct/peer/informe.pdf',
  ...extra,
});

describe('openAttachment', () => {
  beforeEach(() => {
    native.exists = true;
    native.sharingAvailable = true;
    native.shareError = null;
    native.shares = [];
  });

  it('entrega el archivo a la hoja del sistema con su mime y su nombre', async () => {
    await expect(openAttachment(info())).resolves.toBe('opened');
    expect(native.shares).toEqual([{
      uri: 'file:///data/app/attachments/direct/peer/informe.pdf',
      options: { mimeType: 'application/pdf', dialogTitle: 'informe.pdf' },
    }]);
  });

  it('no intenta abrir nada si el mensaje no trae ruta', async () => {
    await expect(openAttachment(info({ localPath: undefined }))).resolves.toBe('not-downloaded');
    expect(native.shares).toEqual([]);
  });

  it('avisa cuando el archivo ya no está en el dispositivo', async () => {
    native.exists = false;

    await expect(openAttachment(info())).resolves.toBe('missing');
    expect(native.shares).toEqual([]);
  });

  it('avisa cuando no hay ninguna app capaz de abrirlo', async () => {
    native.sharingAvailable = false;

    await expect(openAttachment(info())).resolves.toBe('unsupported');
    expect(native.shares).toEqual([]);
  });

  it('trata un fallo del sistema como archivo no abrible', async () => {
    native.shareError = new Error('Activity not found');

    await expect(openAttachment(info())).resolves.toBe('unsupported');
  });
});

describe('attachmentOpenMessage', () => {
  it('no muestra nada cuando el archivo se abrió', () => {
    expect(attachmentOpenMessage('opened')).toBeNull();
  });

  it('explica el motivo en el resto de casos', () => {
    expect(attachmentOpenMessage('missing')).toBe('El archivo ya no está en este dispositivo');
    expect(attachmentOpenMessage('unsupported')).toBe('No hay ninguna app para abrir este archivo');
    expect(attachmentOpenMessage('not-downloaded')).toBe('El archivo todavía no se ha descargado');
  });
});

describe('planAttachmentOpen', () => {
  it('pide el archivo cuando el mensaje no trae ruta y se puede reclamar', () => {
    expect(planAttachmentOpen(info({ localPath: undefined }) as never, { exists: false, requestable: true }))
      .toEqual({ kind: 'request' });
  });

  it('avisa cuando falta el archivo y no hay a quién pedirlo', () => {
    expect(planAttachmentOpen(info({ localPath: undefined }) as never, { exists: false, requestable: false }))
      .toEqual({ kind: 'notice', message: 'El archivo todavía no se ha descargado' });
  });

  it('pide el archivo cuando la imagen ya no está en el dispositivo', () => {
    const image = info({ kind: 'image', mimeType: 'image/jpeg', fileName: 'foto.jpg' });
    expect(planAttachmentOpen(image as never, { exists: false, requestable: true })).toEqual({ kind: 'request' });
    expect(planAttachmentOpen(image as never, { exists: false, requestable: false }))
      .toEqual({ kind: 'notice', message: 'El archivo ya no está en este dispositivo' });
  });

  it('abre la imagen cuando sí está', () => {
    const image = info({ kind: 'image', mimeType: 'image/jpeg', fileName: 'foto.jpg' });
    expect(planAttachmentOpen(image as never, { exists: true, requestable: true })).toEqual({ kind: 'media' });
  });

  it('comparte el documento cuando está disponible', () => {
    expect(planAttachmentOpen(info() as never, { exists: true, requestable: true })).toEqual({ kind: 'share' });
  });

  it('no intenta compartir un documento que ya no está', () => {
    expect(planAttachmentOpen(info() as never, { exists: false, requestable: true })).toEqual({ kind: 'request' });
  });

  it('pide a mis otros dispositivos cuando el archivo es mío y no está aquí', () => {
    expect(planAttachmentOpen(info({ localPath: undefined }) as never, { exists: false, requestable: true, isMine: true }))
      .toEqual({ kind: 'own-devices' });
    const image = info({ kind: 'image', mimeType: 'image/jpeg', fileName: 'foto.jpg' });
    expect(planAttachmentOpen(image as never, { exists: false, requestable: true, isMine: true }))
      .toEqual({ kind: 'own-devices' });
  });
});
