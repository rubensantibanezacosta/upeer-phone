import Storage from 'expo-sqlite/kv-store';
import * as LocalAuthentication from 'expo-local-authentication';

import {
  biometricErrorMessage,
  biometricPromptTitle,
  canUseBiometrics,
  type BiometricAvailability,
  type BiometricKind,
} from '@/domain/biometric-lock';

const STORAGE_KEY = 'upeer.biometricLock';

const listeners = new Set<() => void>();

function emit() {
  for (const listener of listeners) listener();
}

function kindFor(types: LocalAuthentication.AuthenticationType[]): BiometricKind {
  if (types.includes(LocalAuthentication.AuthenticationType.FINGERPRINT)) return 'fingerprint';
  if (types.includes(LocalAuthentication.AuthenticationType.FACIAL_RECOGNITION)) return 'face';
  if (types.includes(LocalAuthentication.AuthenticationType.IRIS)) return 'iris';
  return 'none';
}

export async function readBiometricEnabled(): Promise<boolean> {
  return (await Storage.getItemAsync(STORAGE_KEY)) === 'on';
}

export async function writeBiometricEnabled(enabled: boolean): Promise<void> {
  await Storage.setItemAsync(STORAGE_KEY, enabled ? 'on' : 'off');
  emit();
}

export function subscribeBiometricLock(listener: () => void): () => void {
  listeners.add(listener);
  return () => { listeners.delete(listener); };
}

export async function readBiometricAvailability(): Promise<BiometricAvailability> {
  const [hasHardware, enrolled, types, enabled] = await Promise.all([
    LocalAuthentication.hasHardwareAsync(),
    LocalAuthentication.isEnrolledAsync(),
    LocalAuthentication.supportedAuthenticationTypesAsync(),
    readBiometricEnabled(),
  ]);
  const kind = hasHardware ? kindFor(types) : 'none';
  const availability: BiometricAvailability = { enabled, enrolled, hasHardware, kind };
  return { ...availability, enabled: enabled && canUseBiometrics(availability) };
}

export type BiometricCheck = { message: string; success: boolean };

export async function askForBiometrics(kind: BiometricKind): Promise<BiometricCheck> {
  const result = await LocalAuthentication.authenticateAsync({
    cancelLabel: 'Ahora no',
    disableDeviceFallback: true,
    fallbackLabel: 'Usar el bloqueo del teléfono',
    promptMessage: biometricPromptTitle(kind),
  });
  if (result.success) return { message: '', success: true };
  return { message: biometricErrorMessage(result.error), success: false };
}
