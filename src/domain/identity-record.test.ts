import { describe, expect, it } from 'vitest';

import { deviceIdentity, isRootIdentity, normalizeStoredIdentity } from './identity-record';

const upeerId = 'aa'.repeat(32);
const publicKey = 'bb'.repeat(32);

describe('normalizeStoredIdentity', () => {
  it('migra una identidad guardada sin modo a raíz', () => {
    const identity = normalizeStoredIdentity({ mnemonic: 'doce palabras', upeerId, publicKey, secretKey: 'cc'.repeat(64) });

    expect(identity).not.toBeNull();
    expect(identity?.mode).toBe('root');
    expect(isRootIdentity(identity!)).toBe(true);
  });

  it('conserva el modo raíz explícito', () => {
    const identity = normalizeStoredIdentity({ mode: 'root', mnemonic: '', upeerId, publicKey, secretKey: 'cc'.repeat(64) });

    expect(identity?.mode).toBe('root');
  });

  it('acepta una identidad de dispositivo sin secreto', () => {
    const identity = normalizeStoredIdentity({ mode: 'device', upeerId, publicKey });

    expect(identity).toEqual(deviceIdentity(upeerId, publicKey));
    expect(isRootIdentity(identity!)).toBe(false);
  });

  it('descarta una identidad sin secreto y sin modo', () => {
    expect(normalizeStoredIdentity({ upeerId, publicKey })).toBeNull();
  });

  it('descarta valores que no son objetos', () => {
    expect(normalizeStoredIdentity(null)).toBeNull();
    expect(normalizeStoredIdentity('identidad')).toBeNull();
    expect(normalizeStoredIdentity(42)).toBeNull();
  });

  it('descarta una identidad sin upeerId o sin clave pública', () => {
    expect(normalizeStoredIdentity({ mode: 'device', upeerId: '', publicKey })).toBeNull();
    expect(normalizeStoredIdentity({ mode: 'device', upeerId, publicKey: '' })).toBeNull();
  });
});

describe('deviceIdentity', () => {
  it('no incluye frase ni clave secreta', () => {
    const identity = deviceIdentity(upeerId, publicKey);

    expect(identity).toEqual({ mode: 'device', upeerId, publicKey });
    expect('secretKey' in identity).toBe(false);
    expect('mnemonic' in identity).toBe(false);
  });
});
