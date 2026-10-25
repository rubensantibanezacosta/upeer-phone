import { useEffect, useState } from 'react';

import { getSyncStatus } from '../../modules/upeer-transport/src/UPeerTransportModule';
import type { SyncTransportStatus } from '../../modules/upeer-transport/src/UPeerTransport.types';

const INITIAL: SyncTransportStatus = { state: 'idle', devices: 0, devicesDone: 0, pendingEvents: 0, unsupported: 0, lastPeerAt: 0, ageMillis: 0 };

export function useSyncStatus(): SyncTransportStatus {
  const [status, setStatus] = useState<SyncTransportStatus>(INITIAL);

  useEffect(() => {
    let active = true;
    const read = () => {
      void getSyncStatus()
        .then((next) => {
          if (active) setStatus(next);
        })
        .catch((cause: unknown) => console.warn('No se pudo leer el estado de sincronización.', cause));
    };
    read();
    const timer = setInterval(read, 2000);
    return () => {
      active = false;
      clearInterval(timer);
    };
  }, []);

  return status;
}
