import { useEffect, useState } from 'react';

import type { DhtLocationStatus } from '../../modules/upeer-transport/src/UPeerTransport.types';
import { getDhtLocationStatus } from '../../modules/upeer-transport/src/UPeerTransportModule';

const REFRESH_MS = 15_000;

export type LocationStatusSnapshot = {
  status: DhtLocationStatus | null;
  updatedAt: number;
};

export function useLocationStatus(active = true): LocationStatusSnapshot {
  const [snapshot, setSnapshot] = useState<LocationStatusSnapshot>({ status: null, updatedAt: 0 });

  useEffect(() => {
    if (!active) return;
    let cancelled = false;
    const load = () => {
      getDhtLocationStatus()
        .then((next) => {
          if (!cancelled) setSnapshot({ status: next, updatedAt: Date.now() });
        })
        .catch((cause: unknown) => {
          console.warn('No se pudo leer el estado de la ubicación.', cause);
        });
    };
    load();
    const timer = setInterval(load, REFRESH_MS);
    return () => {
      cancelled = true;
      clearInterval(timer);
    };
  }, [active]);

  return snapshot;
}
