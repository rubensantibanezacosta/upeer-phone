import { describe, expect, it } from 'vitest';

import { describeVaultEntries, describeVaultHint, EMPTY_VAULT_USAGE, vaultFillPercent } from '@/domain/vault-usage';
import type { VaultUsage } from '../../modules/upeer-transport/src/UPeerTransportModule';

const FULL: VaultUsage = { bytes: 0, entries: 2000, expiringSoon: 2, maxEntries: 2000 };
const HALF: VaultUsage = { bytes: 1536, entries: 1000, expiringSoon: 0, maxEntries: 2000 };
const EMPTY: VaultUsage = EMPTY_VAULT_USAGE;

describe('describeVaultEntries', () => {
  it('muestra el recuento y el tope', () => {
    expect(describeVaultEntries(HALF)).toBe('1.000 de 2.000 mensajes');
  });

  it('singulariza con un solo mensaje', () => {
    expect(describeVaultEntries({ ...EMPTY, maxEntries: 0, entries: 1 })).toBe('1 mensaje');
  });

  it('no se apoya en el tope cuando el transporte no lo informa', () => {
    expect(describeVaultEntries({ ...EMPTY, entries: 3 })).toBe('3 mensajes');
  });

  it('sanea valores negativos o no finitos', () => {
    expect(describeVaultEntries({ bytes: 0, entries: -5, expiringSoon: 0, maxEntries: Number.NaN })).toBe('0 mensajes');
  });
});

describe('vaultFillPercent', () => {
  it('redondea el porcentaje de ocupación', () => {
    expect(vaultFillPercent(HALF)).toBe(50);
    expect(vaultFillPercent(FULL)).toBe(100);
  });

  it('devuelve 0 sin tope', () => {
    expect(vaultFillPercent({ ...EMPTY, entries: 10 })).toBe(0);
  });
});

describe('describeVaultHint', () => {
  it('habla de un vault vacío', () => {
    expect(describeVaultHint(EMPTY)).toBe('No custodias ningún mensaje pendiente.');
  });

  it('avisa de los que caducan pronto', () => {
    expect(describeVaultHint(FULL)).toContain('2 caducan en las próximas 24 horas.');
  });

  it('singulariza el aviso de caducidad', () => {
    expect(describeVaultHint({ ...HALF, expiringSoon: 1 })).toContain('Uno caduca en las próximas 24 horas.');
  });

  it('deja solo la explicación sin caducidades próximas', () => {
    expect(describeVaultHint(HALF)).toBe('Cada mensaje se borra al entregarse a su destinatario o al caducar.');
  });
});
