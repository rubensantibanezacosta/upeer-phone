export type BiometricKind = 'face' | 'fingerprint' | 'iris' | 'none';

export type BiometricAvailability = {
  enabled: boolean;
  enrolled: boolean;
  hasHardware: boolean;
  kind: BiometricKind;
};

export const BIOMETRIC_UNAVAILABLE_TITLE = 'Huella no disponible';

export function biometricKindLabel(kind: BiometricKind): string {
  if (kind === 'face') return 'Reconocimiento facial';
  if (kind === 'fingerprint') return 'Huella';
  if (kind === 'iris') return 'Iris';
  return 'Ninguno';
}

export function biometricPromptTitle(kind: BiometricKind): string {
  return kind === 'none' ? 'Desbloquea uPeer' : `Desbloquea uPeer con ${biometricKindLabel(kind).toLowerCase()}`;
}

export function canUseBiometrics(availability: BiometricAvailability): boolean {
  return availability.hasHardware && availability.enrolled && availability.kind !== 'none';
}

export function biometricRowTitle(availability: BiometricAvailability): string {
  if (!availability.hasHardware) return 'Este teléfono no tiene lector';
  if (!availability.enrolled) return 'Añade una huella en los ajustes del teléfono';
  return availability.enabled ? `Bloqueo con ${biometricKindLabel(availability.kind).toLowerCase()} activo` : `Activar el bloqueo con ${biometricKindLabel(availability.kind).toLowerCase()}`;
}

export function biometricRowDetail(availability: BiometricAvailability): string {
  if (!availability.hasHardware) return 'Sin lector de huellas no se puede bloquear la app en este dispositivo';
  if (!availability.enrolled) return 'Cuando haya una huella registrada podrás activarlo';
  return availability.enabled ? 'Se pide al abrir la app y al volver a ella' : 'Se pedirá al abrir la app y al volver a ella';
}

export function biometricErrorMessage(code: string): string {
  if (code === 'not_enrolled') return 'No hay ninguna huella registrada en el teléfono';
  if (code === 'not_available') return 'El lector no está disponible ahora mismo';
  if (code === 'lockout') return 'Demasiados intentos: usa el desbloqueo del teléfono y vuelve a probar';
  if (code === 'user_cancel' || code === 'system_cancel' || code === 'app_cancel') return '';
  return 'No se pudo comprobar la huella';
}
