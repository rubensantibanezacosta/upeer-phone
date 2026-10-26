import { useCallback } from 'react';

import { useSnackbar } from '@/components/snackbar';
import { saveContact, type UPeerContact } from '@/storage/contact-store';

type RequestResolutionParams = {
  name: string;
  onResolved: (status: UPeerContact['status']) => void;
  upeerId: string | undefined;
};

export function useRequestResolution({ name, onResolved, upeerId }: RequestResolutionParams) {
  const { showSnackbar } = useSnackbar();

  const resolve = useCallback((status: UPeerContact['status'], message: string) => {
    if (!upeerId) return;
    saveContact({ address: '', createdAt: Date.now(), name: name || upeerId.slice(0, 12), publicKey: null, status, upeerId })
      .then(() => {
        onResolved(status);
        showSnackbar(message);
      })
      .catch((cause: unknown) => {
        console.warn('No se pudo resolver la solicitud de mensaje.', cause);
        showSnackbar('No se pudo resolver la solicitud');
      });
  }, [name, onResolved, showSnackbar, upeerId]);

  return {
    accept: useCallback(() => resolve('connected', 'Solicitud aceptada'), [resolve]),
    block: useCallback(() => resolve('blocked', 'Remitente bloqueado'), [resolve]),
  };
}
