import { describe, expect, it } from 'vitest';

import {
  biometricErrorMessage,
  biometricKindLabel,
  biometricPromptTitle,
  biometricRowDetail,
  biometricRowTitle,
  canUseBiometrics,
  type BiometricAvailability,
} from './biometric-lock';

const base: BiometricAvailability = { enabled: false, enrolled: true, hasHardware: true, kind: 'fingerprint' };

describe('biometricKindLabel', () => {
  it('nombra cada lector en español', () => {
    expect(biometricKindLabel('fingerprint')).toBe('Huella');
    expect(biometricKindLabel('face')).toBe('Reconocimiento facial');
    expect(biometricKindLabel('iris')).toBe('Iris');
    expect(biometricKindLabel('none')).toBe('Ninguno');
  });
});

describe('biometricPromptTitle', () => {
  it('menciona el lector disponible', () => {
    expect(biometricPromptTitle('fingerprint')).toBe('Desbloquea uPeer con huella');
    expect(biometricPromptTitle('none')).toBe('Desbloquea uPeer');
  });
});

describe('canUseBiometrics', () => {
  it('exige hardware y huella registrada', () => {
    expect(canUseBiometrics(base)).toBe(true);
    expect(canUseBiometrics({ ...base, hasHardware: false, kind: 'none' })).toBe(false);
    expect(canUseBiometrics({ ...base, enrolled: false })).toBe(false);
    expect(canUseBiometrics({ ...base, kind: 'none' })).toBe(false);
  });
});

describe('biometricRowTitle', () => {
  it('explica por qué no se puede activar', () => {
    expect(biometricRowTitle({ ...base, hasHardware: false, kind: 'none' })).toBe('Este teléfono no tiene lector');
    expect(biometricRowTitle({ ...base, enrolled: false })).toBe('Añade una huella en los ajustes del teléfono');
    expect(biometricRowTitle(base)).toBe('Activar el bloqueo con huella');
    expect(biometricRowTitle({ ...base, enabled: true })).toBe('Bloqueo con huella activo');
  });
});

describe('biometricRowDetail', () => {
  it('acompaña al título sin contradecirlo', () => {
    expect(biometricRowDetail({ ...base, hasHardware: false, kind: 'none' })).toContain('Sin lector');
    expect(biometricRowDetail(base)).toBe('Se pedirá al abrir la app y al volver a ella');
    expect(biometricRowDetail({ ...base, enabled: true })).toBe('Se pide al abrir la app y al volver a ella');
  });
});

describe('biometricErrorMessage', () => {
  it('traduce los fallos y calla las cancelaciones', () => {
    expect(biometricErrorMessage('not_enrolled')).toContain('huella registrada');
    expect(biometricErrorMessage('lockout')).toContain('Demasiados intentos');
    expect(biometricErrorMessage('user_cancel')).toBe('');
    expect(biometricErrorMessage('system_cancel')).toBe('');
    expect(biometricErrorMessage('app_cancel')).toBe('');
    expect(biometricErrorMessage('otro')).toBe('No se pudo comprobar la huella');
  });
});
