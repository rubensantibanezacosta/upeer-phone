import { useCallback, useEffect, useState } from 'react';

import {
  acceptEnrollmentRequest,
  cancelAllEnrollmentInvites,
  cancelEnrollmentInvite,
  completeEnrollmentAuthorization,
  createEnrollmentInvite,
  getEnrollmentAuthorizerStatus,
  getLastIssuedEnrollment,
} from '../../modules/upeer-transport/src/UPeerTransportModule';
import type {
  EnrollmentAuthorizerInvite,
  EnrollmentAuthorizerIssued,
  EnrollmentAuthorizerRequest,
  EnrollmentAuthorizerStatus,
} from '../../modules/upeer-transport/src/UPeerTransport.types';

export type EnrollmentAuthorizerState = {
  invite: EnrollmentAuthorizerInvite | null;
  accepted: EnrollmentAuthorizerRequest | null;
  issued: (EnrollmentAuthorizerIssued & { responseCode: string }) | null;
  invites: EnrollmentAuthorizerStatus['invites'];
  busy: boolean;
  error: string;
};

const initialState: EnrollmentAuthorizerState = {
  invite: null,
  accepted: null,
  issued: null,
  invites: [],
  busy: false,
  error: '',
};

export function useEnrollmentAuthorizer() {
  const [state, setState] = useState<EnrollmentAuthorizerState>(initialState);

  const refresh = useCallback(async () => {
    try {
      const status = await getEnrollmentAuthorizerStatus();
      setState((current) => ({ ...current, invites: status.invites }));
    } catch (cause) {
      console.warn('No se pudo consultar el estado del autorizador de dispositivos.', cause);
    }
  }, []);

  useEffect(() => {
    let active = true;
    getEnrollmentAuthorizerStatus()
      .then((status) => {
        if (active) setState((current) => ({ ...current, invites: status.invites }));
      })
      .catch((cause: unknown) => {
        console.warn('No se pudo consultar el estado del autorizador de dispositivos.', cause);
      });
    return () => { active = false; };
  }, []);

  const start = useCallback(async () => {
    setState((current) => ({ ...current, busy: true, error: '' }));
    try {
      const invite = await createEnrollmentInvite();
      setState((current) => ({ ...current, invite, accepted: null, issued: null, busy: false }));
      await refresh();
    } catch (cause) {
      setState((current) => ({ ...current, busy: false, error: describe(cause, 'No se pudo generar la invitación.') }));
    }
  }, [refresh]);

  const inviteId = state.invite?.inviteId ?? '';

  useEffect(() => {
    if (!inviteId) return;
    let active = true;
    const timer = setInterval(() => {
      getLastIssuedEnrollment()
        .then((issued) => {
          if (!active || !issued || issued.inviteId !== inviteId) return;
          setState((current) => (current.issued ? current : { ...current, issued, invite: null, accepted: null, busy: false }));
          void refresh();
        })
        .catch((cause: unknown) => {
          console.warn('No se pudo consultar el último dispositivo autorizado.', cause);
        });
    }, 1500);
    return () => {
      active = false;
      clearInterval(timer);
    };
  }, [inviteId, refresh]);

  const accept = useCallback(async (code: string) => {
    const normalized = code.trim();
    if (!normalized) {
      setState((current) => ({ ...current, error: 'Pega el código de solicitud del dispositivo nuevo.' }));
      return;
    }
    setState((current) => ({ ...current, busy: true, error: '' }));
    try {
      const accepted = await acceptEnrollmentRequest(normalized);
      setState((current) => ({ ...current, accepted, issued: null, busy: false }));
    } catch (cause) {
      setState((current) => ({ ...current, busy: false, error: describe(cause, 'No se pudo leer la solicitud.') }));
    }
  }, []);

  const issue = useCallback(async () => {
    if (!state.accepted) {
      setState((current) => ({ ...current, error: 'Primero acepta la solicitud del dispositivo nuevo.' }));
      return;
    }
    setState((current) => ({ ...current, busy: true, error: '' }));
    try {
      const issued = await completeEnrollmentAuthorization(state.accepted);
      setState((current) => ({ ...current, issued, invite: null, accepted: null, busy: false }));
      await refresh();
    } catch (cause) {
      setState((current) => ({ ...current, busy: false, error: describe(cause, 'No se pudo emitir el certificado.') }));
    }
  }, [refresh, state.accepted]);

  const cancel = useCallback(async (inviteId: string) => {
    try {
      await cancelEnrollmentInvite(inviteId);
    } catch (cause) {
      console.warn('No se pudo cancelar la invitación.', cause);
    }
    await refresh();
  }, [refresh]);

  const reset = useCallback(async () => {
    try {
      await cancelAllEnrollmentInvites();
    } catch (cause) {
      console.warn('No se pudieron cancelar las invitaciones pendientes.', cause);
    }
    setState(initialState);
  }, []);

  return { state, start, accept, issue, cancel, reset, refresh };
}

function describe(cause: unknown, fallback: string): string {
  if (cause instanceof Error && cause.message.trim()) return cause.message;
  return fallback;
}
