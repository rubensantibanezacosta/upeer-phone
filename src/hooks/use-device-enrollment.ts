import { useCallback, useEffect, useRef, useState } from 'react';

import {
  beginDeviceEnrollment,
  cancelDeviceEnrollment,
  finishDeviceEnrollment,
  getDeviceEnrollmentStatus,
  getNodeInfo,
  startWithPeers,
  submitDeviceEnrollment,
} from '../../modules/upeer-transport/src/UPeerTransportModule';
import { loadOrCreateYggdrasilSeed, replaceYggdrasilSeed } from '@/storage/identity-store';
import type {
  DeviceEnrollmentBeginResult,
  EnrollmentCertificateSummary,
} from '../../modules/upeer-transport/src/UPeerTransport.types';

export type DeviceEnrollmentPhase = 'idle' | 'sending' | 'waiting' | 'manual';

export type DeviceEnrollmentState = {
  phase: DeviceEnrollmentPhase;
  offer: DeviceEnrollmentBeginResult['offer'] | null;
  requestCode: string;
  installed: EnrollmentCertificateSummary | null;
  busy: boolean;
  error: string;
};

const initialState: DeviceEnrollmentState = { phase: 'idle', offer: null, requestCode: '', installed: null, busy: false, error: '' };

const POLL_INTERVAL_MS = 1500;
const POLL_TIMEOUT_MS = 120_000;

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function ensureNetworkNode(): Promise<void> {
  const current = await getNodeInfo().catch(() => null);
  if (current?.address) return;
  let seed = await loadOrCreateYggdrasilSeed();
  try {
    await startWithPeers([], seed);
  } catch (cause) {
    if (!isKeyMismatch(cause)) {
      console.warn('No se pudo levantar el nodo para el emparejamiento.', cause);
      return;
    }
    seed.fill(0);
    seed = await replaceYggdrasilSeed();
    await startWithPeers([], seed);
  } finally {
    seed.fill(0);
  }
}

function isKeyMismatch(cause: unknown): boolean {
  const message = cause instanceof Error ? cause.message.toLowerCase() : '';
  return message.includes('key') && (message.includes('does not match') || message.includes('mismatch') || message.includes('certificate'));
}

export function useDeviceEnrollment() {
  const [state, setState] = useState<DeviceEnrollmentState>(initialState);
  const active = useRef(true);

  useEffect(() => {
    active.current = true;
    getDeviceEnrollmentStatus()
      .then((status) => {
        if (active.current && status.installed) setState((current) => ({ ...current, installed: status.installed ?? null }));
      })
      .catch((cause: unknown) => {
        console.warn('No se pudo consultar el estado de emparejamiento.', cause);
      });
    return () => { active.current = false; };
  }, []);

  const waitForInstall = useCallback(async (): Promise<EnrollmentCertificateSummary | null> => {
    const deadline = Date.now() + POLL_TIMEOUT_MS;
    while (active.current && Date.now() < deadline) {
      const status = await getDeviceEnrollmentStatus().catch(() => null);
      if (status?.installed) return status.installed;
      await delay(POLL_INTERVAL_MS);
    }
    return null;
  }, []);

  const linkAccount = useCallback(async (code: string): Promise<EnrollmentCertificateSummary | null> => {
    const normalized = code.trim();
    if (!normalized) {
      setState((current) => ({ ...current, error: 'Escanea el QR del dispositivo que ya tiene la cuenta.' }));
      return null;
    }
    setState((current) => ({ ...current, busy: true, error: '', phase: 'sending' }));
    try {
      const result = await beginDeviceEnrollment(normalized);
      if (!result.offer.authorizerAddress) {
        setState({ phase: 'manual', offer: result.offer, requestCode: result.requestCode, installed: null, busy: false, error: '' });
        return null;
      }
      await ensureNetworkNode();
      await submitDeviceEnrollment();
      setState({ phase: 'waiting', offer: result.offer, requestCode: '', installed: null, busy: false, error: '' });
      const installed = await waitForInstall();
      if (!active.current) return null;
      if (!installed) {
        setState((current) => ({ ...current, phase: 'manual', busy: false, error: 'La cuenta no respondió a tiempo. Puedes terminar con los códigos manuales.' }));
        return null;
      }
      setState({ phase: 'idle', offer: null, requestCode: '', installed, busy: false, error: '' });
      return installed;
    } catch (cause) {
      if (!active.current) return null;
      setState((current) => ({ ...current, busy: false, phase: 'manual', error: describe(cause, 'No se pudo entregar la solicitud al dispositivo que autoriza.') }));
      return null;
    }
  }, [waitForInstall]);

  const begin = useCallback(async (code: string) => {
    const normalized = code.trim();
    if (!normalized) {
      setState((current) => ({ ...current, error: 'Pega el código de invitación del dispositivo autorizado.' }));
      return;
    }
    setState((current) => ({ ...current, busy: true, error: '' }));
    try {
      const result = await beginDeviceEnrollment(normalized);
      setState({ phase: 'manual', offer: result.offer, requestCode: result.requestCode, installed: null, busy: false, error: '' });
    } catch (cause) {
      setState((current) => ({ ...current, busy: false, error: describe(cause, 'No se pudo leer la invitación.') }));
    }
  }, []);

  const finish = useCallback(async (code: string) => {
    const normalized = code.trim();
    if (!normalized) {
      setState((current) => ({ ...current, error: 'Pega el código de respuesta que muestra el dispositivo autorizado.' }));
      return null;
    }
    setState((current) => ({ ...current, busy: true, error: '' }));
    try {
      const installed = await finishDeviceEnrollment(normalized);
      setState({ phase: 'idle', offer: null, requestCode: '', installed, busy: false, error: '' });
      return installed;
    } catch (cause) {
      setState((current) => ({ ...current, busy: false, error: describe(cause, 'No se pudo instalar el certificado.') }));
      return null;
    }
  }, []);

  const cancel = useCallback(async () => {
    try {
      await cancelDeviceEnrollment();
    } catch (cause) {
      console.warn('No se pudo cancelar el emparejamiento.', cause);
    }
    setState(initialState);
  }, []);

  const clearError = useCallback(() => {
    setState((current) => (current.error ? { ...current, error: '' } : current));
  }, []);

  return { state, linkAccount, begin, finish, cancel, clearError };
}

function describe(cause: unknown, fallback: string): string {
  if (cause instanceof Error && cause.message.trim()) return cause.message;
  return fallback;
}
