import { useCallback, useState } from 'react';

import type { PeerSecurity } from '../../modules/upeer-transport/src/UPeerTransport.types';
import { getPeerSecurity } from '../../modules/upeer-transport/src/UPeerTransportModule';

export type PeerSecurityState = {
  error: string;
  loading: boolean;
  security: PeerSecurity | null;
};

const INITIAL: PeerSecurityState = { error: '', loading: false, security: null };

export function usePeerSecurity() {
  const [state, setState] = useState<PeerSecurityState>(INITIAL);

  const load = useCallback((upeerId: string) => {
    setState({ ...INITIAL, loading: true });
    getPeerSecurity(upeerId)
      .then((security) => setState({ error: '', loading: false, security }))
      .catch((cause: unknown) => {
        console.warn('No se pudo leer la sesión cifrada del contacto.', cause);
        setState({ error: 'No se pudo leer la sesión cifrada', loading: false, security: null });
      });
  }, []);

  const reset = useCallback(() => setState(INITIAL), []);

  return { load, reset, state };
}
