import { beforeEach, describe, expect, it, vi } from 'vitest';

import { guessMimeType, fileNameOf, pickedAttachment, pickAttachment, pickResultMessage, toFilePath } from './attachment-picker';

const native = vi.hoisted(() => ({
  cameraGranted: true,
  libraryGranted: true,
  camera: { canceled: true, assets: [] } as { canceled: boolean; assets: { uri: string; fileName?: string | null; mimeType?: string | null }[] },
  library: { canceled: true, assets: [] } as { canceled: boolean; assets: { uri: string; fileName?: string | null; mimeType?: string | null }[] },
  document: { canceled: true, assets: [] } as { canceled: boolean; assets: { uri: string; name?: string | null; mimeType?: string | null }[] },
  failure: null as Error | null,
}));

vi.mock('expo-image-picker', () => ({
  requestCameraPermissionsAsync: async () => ({ granted: native.cameraGranted }),
  requestMediaLibraryPermissionsAsync: async () => ({ granted: native.libraryGranted }),
  launchCameraAsync: async () => {
    if (native.failure) throw native.failure;
    return native.camera;
  },
  launchImageLibraryAsync: async () => {
    if (native.failure) throw native.failure;
    return native.library;
  },
}));

vi.mock('expo-document-picker', () => ({
  getDocumentAsync: async () => {
    if (native.failure) throw native.failure;
    return native.document;
  },
}));

beforeEach(() => {
  native.cameraGranted = true;
  native.libraryGranted = true;
  native.camera = { canceled: true, assets: [] };
  native.library = { canceled: true, assets: [] };
  native.document = { canceled: true, assets: [] };
  native.failure = null;
});

describe('fileNameOf', () => {
  it('extrae el nombre decodificado de la ruta', () => {
    expect(fileNameOf('file:///cache/ImagePicker/foto%20uno.jpg')).toBe('foto uno.jpg');
  });

  it('usa el valor por defecto si la ruta no tiene nombre', () => {
    expect(fileNameOf('file:///cache/', 'archivo')).toBe('archivo');
  });
});

describe('guessMimeType', () => {
  it('reconoce las extensiones conocidas', () => {
    expect(guessMimeType('informe.PDF')).toBe('application/pdf');
    expect(guessMimeType('foto.jpeg')).toBe('image/jpeg');
    expect(guessMimeType('nota.m4a')).toBe('audio/mp4');
  });

  it('cae en binario cuando no hay extensión o es desconocida', () => {
    expect(guessMimeType('sin-extension')).toBe('application/octet-stream');
    expect(guessMimeType('raro.xyz')).toBe('application/octet-stream');
  });
});

describe('toFilePath', () => {
  it('quita el esquema file:// y decodifica', () => {
    expect(toFilePath('file:///cache/a%20b.pdf')).toBe('/cache/a b.pdf');
  });

  it('deja intacta una ruta sin esquema', () => {
    expect(toFilePath('/cache/a.pdf')).toBe('/cache/a.pdf');
  });
});

describe('pickedAttachment', () => {
  it('prefiere el nombre y el tipo que da el sistema', () => {
    expect(pickedAttachment('file:///cache/x.bin', 'informe.pdf', 'application/pdf')).toEqual({
      path: '/cache/x.bin',
      fileName: 'informe.pdf',
      mimeType: 'application/pdf',
    });
  });

  it('deduce nombre y tipo cuando faltan', () => {
    expect(pickedAttachment('file:///cache/foto.jpg')).toEqual({
      path: '/cache/foto.jpg',
      fileName: 'foto.jpg',
      mimeType: 'image/jpeg',
    });
  });
});

describe('pickAttachment', () => {
  it('devuelve cancelado cuando el usuario cierra la galería', async () => {
    expect(await pickAttachment('library')).toEqual({ status: 'cancelled' });
  });

  it('devuelve el archivo elegido en la galería', async () => {
    native.library = { canceled: false, assets: [{ fileName: 'foto.jpg', mimeType: 'image/jpeg', uri: 'file:///cache/foto.jpg' }] };
    expect(await pickAttachment('library')).toEqual({
      status: 'picked',
      attachment: { path: '/cache/foto.jpg', fileName: 'foto.jpg', mimeType: 'image/jpeg' },
    });
  });

  it('avisa cuando falta el permiso de cámara', async () => {
    native.cameraGranted = false;
    expect(await pickAttachment('camera')).toEqual({ status: 'denied' });
  });

  it('devuelve el documento elegido', async () => {
    native.document = { canceled: false, assets: [{ mimeType: 'application/pdf', name: 'informe.pdf', uri: 'file:///cache/informe.pdf' }] };
    expect(await pickAttachment('document')).toEqual({
      status: 'picked',
      attachment: { path: '/cache/informe.pdf', fileName: 'informe.pdf', mimeType: 'application/pdf' },
    });
  });

  it('devuelve fallo cuando el selector lanza', async () => {
    native.failure = new Error('sin actividad');
    expect(await pickAttachment('document')).toEqual({ status: 'failed' });
  });
});

describe('pickResultMessage', () => {
  it('traduce los estados de error', () => {
    expect(pickResultMessage({ status: 'denied' })).toBe('Necesitas dar permiso para adjuntar ese archivo');
    expect(pickResultMessage({ status: 'failed' })).toBe('No se pudo leer el archivo seleccionado');
    expect(pickResultMessage({ status: 'cancelled' })).toBeNull();
  });
});
