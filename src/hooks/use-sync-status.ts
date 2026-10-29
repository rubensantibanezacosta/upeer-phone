import { useSyncExternalStore } from 'react';

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
  filesDone: 0,
  filesTotal: 0,
  filesFailed: 0,
  filesKnown: false,
  headline: 'Sincronizando tus dispositivos…',
  detail: 'Conectando con tus dispositivos',
};

const INITIAL: SyncTransportStatus = {
  state: 'idle',
  gate: 'open',
  devices: 0,
  devicesDone: 0,
  pendingEvents: 0,
  unsupported: 0,
  lastPeerAt: 0,
  ageMillis: 0,
  progress: DISABLED_PROGRESS,
};

const POLL_MS = 2000;

let current: SyncTransportStatus = INITIAL;
let serialized = JSON.stringify(INITIAL);
let timer: ReturnType<typeof setInterval> | null = null;
let reading = false;
const listeners = new Set<() => void>();

function publish(next: SyncTransportStatus): void {
  const encoded = JSON.stringify(next);
  current = next;
  if (encoded === serialized) return;
  serialized = encoded;
  for (const listener of listeners) listener();
}

function read(): void {
  if (reading) return;
  reading = true;
  void getSyncStatus()
    .then((next) => publish({ ...next, progress: next.progress ?? DISABLED_PROGRESS }))
    .catch((cause: unknown) => console.warn('No se pudo leer el estado de sincronización.', cause))
    .finally(() => {
      reading = false;
    });
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  if (timer === null) {
    timer = setInterval(read, POLL_MS);
    read();
  }
  return () => {
    listeners.delete(listener);
    if (listeners.size === 0 && timer !== null) {
      clearInterval(timer);
      timer = null;
    }
  };
}

function snapshot(): SyncTransportStatus {
  return current;
}

export function useSyncStatus(): SyncTransportStatus {
  return useSyncExternalStore(subscribe, snapshot, snapshot);
}
