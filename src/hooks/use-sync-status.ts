import { useEffect, useState } from 'react';

import { getSyncStatus } from '../../modules/upeer-transport/src/UPeerTransportModule';
import type { SyncProgressStatus, SyncTransportStatus } from '../../modules/upeer-transport/src/UPeerTransport.types';

const DISABLED_PROGRESS: SyncProgressStatus = {
  determinate: false,
  percent: 0,
  historyDone: 0,
  historyTotal: 0,
  historyKnown: false,
  pendingEvents: 0,
  pendingFiles: 0,
  headline: 'Sincronizando tus dispositivos…',
  detail: 'Conectando con tus dispositivos',
};

const INITIAL: SyncTransportStatus = {
  state: 'idle',
  devices: 0,
  devicesDone: 0,
  pendingEvents: 0,
  unsupported: 0,
  lastPeerAt: 0,
  ageMillis: 0,
  progress: DISABLED_PROGRESS,
};

export function useSyncStatus(): SyncTransportStatus {
  const [status, setStatus] = useState<SyncTransportStatus>(INITIAL);

  useEffect(() => {
    let active = true;
    const read = () => {
      void getSyncStatus()
        .then((next) => {
          if (active) setStatus({ ...next, progress: next.progress ?? DISABLED_PROGRESS });
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
