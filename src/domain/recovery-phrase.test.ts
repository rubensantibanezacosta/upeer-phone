import { describe, expect, it } from 'vitest';

import { describeRecoveryPhrase } from './recovery-phrase';
import type { DeviceIdentity, RootIdentity } from './identity-record';

const root = (mnemonic: string): RootIdentity => ({ mode: 'root', mnemonic, publicKey: 'pk', secretKey: 'sk', upeerId: 'id' });
const device: DeviceIdentity = { mode: 'device', publicKey: 'pk', upeerId: 'id' };
const PHRASE = 'llama libro perro gato raton verde azul rojo blanco negro gris plata';

describe('describeRecoveryPhrase', () => {
  it('devuelve las doce palabras de una identidad raíz', () => {
    const phrase = describeRecoveryPhrase(root(PHRASE));
    expect(phrase.available).toBe(true);
    expect(phrase.words).toHaveLength(12);
    expect(phrase.words[0]).toBe('llama');
    expect(phrase.reason).toBeNull();
  });

  it('explica por qué no hay frase en una identidad de dispositivo', () => {
    const phrase = describeRecoveryPhrase(device);
    expect(phrase.available).toBe(false);
    expect(phrase.words).toEqual([]);
    expect(phrase.reason).toContain('otro dispositivo');
  });

  it('no inventa palabras cuando la identidad raíz llegó sin frase', () => {
    const phrase = describeRecoveryPhrase(root('   '));
    expect(phrase.available).toBe(false);
    expect(phrase.reason).toContain('sin guardar');
  });
});
