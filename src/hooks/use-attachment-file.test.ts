import { beforeEach, describe, expect, it, vi } from 'vitest';

import { attachmentFileExists } from './use-attachment-file';

const native = vi.hoisted(() => ({ exists: true, throws: false, paths: [] as string[] }));

vi.mock('expo-file-system', () => ({
  File: class {
    constructor(path: string) {
      native.paths.push(path);
      if (native.throws) throw new Error('ruta inválida');
    }

    get exists() {
      return native.exists;
    }
  },
}));

describe('attachmentFileExists', () => {
  beforeEach(() => {
    native.exists = true;
    native.throws = false;
    native.paths = [];
  });

  it('confirma el archivo cuando existe en el dispositivo', () => {
    expect(attachmentFileExists('file:///data/app/nota.m4a')).toBe(true);
  });

  it('descarta el adjunto cuando el archivo ya no está', () => {
    native.exists = false;
    expect(attachmentFileExists('file:///data/app/nota.m4a')).toBe(false);
  });

  it('descarta el adjunto sin ruta local', () => {
    expect(attachmentFileExists(null)).toBe(false);
    expect(native.paths).toEqual([]);
  });

  it('descarta el adjunto cuando la ruta no se puede leer', () => {
    native.throws = true;
    expect(attachmentFileExists('file:///data/app/nota.m4a')).toBe(false);
  });
});
