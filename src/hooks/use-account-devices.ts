import { useCallback, useEffect, useState } from 'react';

import {
  getAccountRegistryDocument,
  getDeviceIdentityStatus,
} from '../../modules/upeer-transport/src/UPeerTransportModule';
import type { AccountRegistryDevice } from '../../modules/upeer-transport/src/UPeerTransport.types';

export type AccountDevicesState = {
  devices: AccountRegistryDevice[];
  localDeviceId?: string;
  loading: boolean;
  error: string;
};

const INITIAL_STATE: AccountDevicesState = { devices: [], loading: true, error: '' };

async function readDevices(): Promise<Omit<AccountDevicesState, 'loading'>> {
  try {
    const [registry, identity] = await Promise.all([getAccountRegistryDocument(), getDeviceIdentityStatus()]);
    const devices = [...(registry.devices ?? [])].sort((left, right) => left.authorizedAt - right.authorizedAt);
    return { devices, error: '', localDeviceId: identity.deviceId };
  } catch (cause) {
    return { devices: [], error: cause instanceof Error ? cause.message : String(cause) };
  }
}

export function useAccountDevices() {
  const [state, setState] = useState<AccountDevicesState>(INITIAL_STATE);

  const refresh = useCallback(async () => {
    const next = await readDevices();
    setState({ ...next, loading: false });
  }, []);

  useEffect(() => {
    let active = true;
    void readDevices().then((next) => {
      if (active) setState({ ...next, loading: false });
    });
    return () => { active = false; };
  }, []);

  return { state, refresh };
}
