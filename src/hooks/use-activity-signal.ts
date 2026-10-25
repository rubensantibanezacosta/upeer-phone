import { useEffect } from 'react';
import { AppState } from 'react-native';

import { markUserActive } from '../../modules/upeer-transport/src/UPeerTransportModule';

export function useActivitySignal(): void {
  useEffect(() => {
    const report = () => {
      markUserActive().catch((cause: unknown) => console.warn('No se pudo anunciar la actividad del usuario.', cause));
    };
    report();
    const subscription = AppState.addEventListener('change', (state) => {
      if (state === 'active') report();
    });
    return () => subscription.remove();
  }, []);
}
