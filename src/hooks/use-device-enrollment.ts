import { useCallback, useEffect, useState } from 'react';

import {
  beginDeviceEnrollment,
  cancelDeviceEnrollment,
  finishDeviceEnrollment,
  getDeviceEnrollmentStatus,
} from '../../modules/upeer-transport/src/UPeerTransportModule';
import type {
  DeviceEnrollmentBeginResult,
  EnrollmentCertificateSummary,
} from '../../modules/upeer-transport/src/UPeerTransport.types';

export type DeviceEnrollmentState = {
  offer: DeviceEnrollmentBeginResult['offer'] | null;
  requestCode: string;
  installed: EnrollmentCertificateSummary | null;
  busy: boolean;
  error: string;
};

const initialState: DeviceEnrollmentState = { offer: null, requestCode: '', installed: null, busy: false, error: '' };

export function useDeviceEnrollment() {
  const [state, setState] = useState<DeviceEnrollmentState>(initialState);

  useEffect(() => {
    let active = true;
    getDeviceEnrollmentStatus()
      .then((status) => {
        if (active && status.installed) setState((current) => ({ ...current, installed: status.installed ?? null }));
      })
      .catch((cause: unknown) => {
        console.warn('No se pudo consultar el estado de emparejamiento.', cause);
      });
    return () => { active = false; };
  }, []);

  const begin = useCallback(async (code: string) => {
    const normalized = code.trim();
    if (!normalized) {
      setState((current) => ({ ...current, error: 'Pega el código de invitación del dispositivo autorizado.' }));
      return;
    }
    setState((current) => ({ ...current, busy: true, error: '' }));
    try {
      const result = await beginDeviceEnrollment(normalized);
      setState({ offer: result.offer, requestCode: result.requestCode, installed: null, busy: false, error: '' });
    } catch (cause) {
      setState((current) => ({ ...current, busy: false, error: describe(cause, 'No se pudo leer la invitación.') }));
    }
  }, []);

  const finish = useCallback(async (code: string) => {
    const normalized = code.trim();
    if (!normalized) {
      setState((current) => ({ ...current, error: 'Pega el código de respuesta que muestra el dispositivo autorizado.' }));
      return;
    }
    setState((current) => ({ ...current, busy: true, error: '' }));
    try {
      const installed = await finishDeviceEnrollment(normalized);
      setState({ offer: null, requestCode: '', installed, busy: false, error: '' });
    } catch (cause) {
      setState((current) => ({ ...current, busy: false, error: describe(cause, 'No se pudo instalar el certificado.') }));
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

  return { state, begin, finish, cancel, clearError };
}

function describe(cause: unknown, fallback: string): string {
  if (cause instanceof Error && cause.message.trim()) return cause.message;
  return fallback;
}
