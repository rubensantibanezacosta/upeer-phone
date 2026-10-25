import { useCallback, useEffect, useState } from 'react';

import {
  getAccountRegistryDocument,
  getAccountRegistryStatus,
  getDeviceIdentityStatus,
} from '../../modules/upeer-transport/src/UPeerTransportModule';
import type { AccountRegistryDevice, AccountRevocation } from '../../modules/upeer-transport/src/UPeerTransport.types';

export type AccountDevicesState = {
  devices: AccountRegistryDevice[];
  revoked: AccountRevocation[];
  localDeviceId?: string;
  localAdmitted: boolean;
  ready: boolean;
  epoch: number;
  loading: boolean;
  error: string;
};

const INITIAL_STATE: AccountDevicesState = { devices: [], revoked: [], localAdmitted: true, ready: false, epoch: 0, loading: true, error: '' };

async function readDevices(): Promise<Omit<AccountDevicesState, 'loading'>> {
  try {
    const [registry, identity, status] = await Promise.all([getAccountRegistryDocument(), getDeviceIdentityStatus(), getAccountRegistryStatus()]);
    const devices = [...(registry.devices ?? [])].sort((left, right) => left.authorizedAt - right.authorizedAt);
    const revoked = [...(registry.revoked ?? [])].sort((left, right) => right.revokedAt - left.revokedAt);
    return {
      devices,
      revoked,
      error: '',
      localDeviceId: identity.deviceId,
      localAdmitted: status.ready ? status.localDeviceAdmitted !== false : true,
      ready: status.ready,
      epoch: status.epoch ?? registry.epoch ?? 0,
    };
  } catch (cause) {
    const message = cause instanceof Error ? cause.message : String(cause);
    const opaque = /JSON|parse|unexpected/i.test(message);
    return { devices: [], revoked: [], localAdmitted: true, ready: false, epoch: 0, error: opaque ? '' : message };
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
