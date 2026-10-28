import { useCallback, useEffect, useState } from 'react';

import { EMPTY_VAULT_USAGE, type VaultUsage } from '@/domain/vault-usage';
import { clearVault, getVaultUsage } from '../../modules/upeer-transport/src/UPeerTransportModule';

export function useVaultUsage(): {
  usage: VaultUsage;
  ready: boolean;
  error: string;
  refresh: () => Promise<void>;
  clear: () => Promise<boolean>;
} {
  const [usage, setUsage] = useState<VaultUsage>(EMPTY_VAULT_USAGE);
  const [ready, setReady] = useState(false);
  const [error, setError] = useState('');

  const refresh = useCallback(async () => {
    try {
      setUsage(await getVaultUsage());
      setError('');
    } catch (cause) {
      console.warn('No se pudo leer el uso del vault.', cause);
      setError('No se pudo leer el vault de este dispositivo');
    } finally {
      setReady(true);
    }
  }, []);

  const clear = useCallback(async (): Promise<boolean> => {
    try {
      await clearVault();
      await refresh();
      return true;
    } catch (cause) {
      console.warn('No se pudo vaciar el vault.', cause);
      setError('No se pudo vaciar el vault');
      return false;
    }
  }, [refresh]);

  useEffect(() => {
    const load = async () => {
      await refresh();
    };
    void load();
  }, [refresh]);

  return { clear, error, ready, refresh, usage };
}
