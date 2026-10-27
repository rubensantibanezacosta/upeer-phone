import { useCallback, useEffect, useRef, useState } from 'react';
import { AppState } from 'react-native';

import { BiometricLockScreen } from '@/components/biometric-lock-screen';
import { canUseBiometrics, type BiometricKind } from '@/domain/biometric-lock';
import { readBiometricAvailability } from '@/services/biometric-lock';

export function BiometricLockGate() {
  const [kind, setKind] = useState<BiometricKind | null>(null);
  const [ready, setReady] = useState(false);
  const current = useRef<string | null | undefined>(AppState.currentState ?? 'active');

  const refresh = useCallback(async () => {
    const availability = await readBiometricAvailability();
    if (!canUseBiometrics(availability) || !availability.enabled) {
      setKind(null);
      return;
    }
    setKind(availability.kind);
  }, []);

  useEffect(() => {
    const timer = setTimeout(() => {
      void refresh().finally(() => setReady(true));
    }, 0);
    return () => clearTimeout(timer);
  }, [refresh]);

  useEffect(() => {
    const subscription = AppState.addEventListener('change', (next) => {
      const previous = current.current;
      current.current = next;
      if (previous === 'background' && next === 'active') void refresh();
    });
    return () => subscription.remove();
  }, [refresh]);

  const unlock = useCallback(() => setKind(null), []);

  if (!ready || kind === null) return null;
  return <BiometricLockScreen key={kind} kind={kind} onUnlocked={unlock} />;
}
