import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { View } from 'react-native';

import { fromHex, type UPeerIdentity } from '@/domain/identity';
import { SessionScreen, type ConnectionSnapshot } from '@/components/session-screen';
import { clearIdentity as clearTransportIdentity, setIdentity as setTransportIdentity, setNotificationPrefs, setReadReceipts, stop } from '../../modules/upeer-transport/src/UPeerTransportModule';
import { clearIdentity, loadProfile, type IdentityProfile } from '@/storage/identity-store';
import { publishNativeProfileSafely } from '@/storage/profile-sync';
import { EMPTY_PEER_MANAGEMENT } from '@/domain/yggdrasil-peers';
import { pushBlockedPeers } from '@/storage/contact-store';
import { usePreferences } from '@/hooks/use-preferences';

export type SessionContextValue = {
  identity: UPeerIdentity;
  address: string;
  connection: ConnectionSnapshot;
  profile: IdentityProfile | null;
  refreshProfile: () => Promise<void>;
  signOut: () => Promise<void>;
};

const SessionContext = createContext<SessionContextValue | null>(null);

export function useSession(): SessionContextValue {
  const value = useContext(SessionContext);
  if (!value) throw new Error('La ruta requiere estar dentro de la sesión autenticada.');
  return value;
}

export function SessionShell({ identity, onSignOut, children }: {
  identity: UPeerIdentity;
  onSignOut: () => void;
  children: React.ReactNode;
}) {
  const [address, setAddress] = useState('');
  const [profile, setProfile] = useState<IdentityProfile | null>(null);
  const [connection, setConnection] = useState<ConnectionSnapshot>({ address: '', phase: 'Conectando a la red…', peers: [], error: '', management: EMPTY_PEER_MANAGEMENT });
  const preferences = usePreferences();
  const refreshProfile = useCallback(async () => {
    try {
      setProfile(await loadProfile());
    } catch (cause) {
      console.warn('No se pudo cargar el perfil de la sesión.', cause);
    }
  }, []);
  useEffect(() => {
    loadProfile().then((stored) => {
      setProfile(stored);
      return publishNativeProfileSafely(stored);
    }).catch((cause: unknown) => {
      console.warn('No se pudo cargar el perfil de la sesión.', cause);
    });
  }, []);
  useEffect(() => {
    pushBlockedPeers().catch((cause: unknown) => {
      console.warn('No se pudo sincronizar la lista de bloqueados al iniciar la sesión.', cause);
    });
  }, []);
  useEffect(() => {
    setNotificationPrefs(JSON.stringify({ messages: preferences.messageNotifications, requests: preferences.requestNotifications, sound: preferences.notificationSound })).catch((cause: unknown) => {
      console.warn('El servicio no recibió las preferencias de notificaciones.', cause);
    });
  }, [preferences.messageNotifications, preferences.notificationSound, preferences.requestNotifications]);
  useEffect(() => {
    setReadReceipts(preferences.readReceipts).catch((cause: unknown) => {
      console.warn('El servicio no recibió la preferencia de confirmaciones de lectura.', cause);
    });
  }, [preferences.readReceipts]);
  useEffect(() => {
    setTransportIdentity(fromHex(identity.secretKey)).catch((cause: unknown) => {
      console.warn('No se pudo provisionar la identidad uPeer en el servicio de red.', cause);
    });
  }, [identity.secretKey]);
  const updateConnection = useCallback((snapshot: ConnectionSnapshot) => setConnection(snapshot), []);
  const updateAddress = useCallback((nextAddress: string) => {
    setAddress(nextAddress);
    setConnection((current) => ({ ...current, address: nextAddress }));
  }, []);
  const signOut = useCallback(async () => {
    try {
      await clearTransportIdentity();
    } catch (cause) {
      console.warn('No se pudo borrar la identidad del servicio de red.', cause);
    }
    try {
      await stop();
    } catch (cause) {
      console.warn('No se pudo detener la sesión de red.', cause);
    }
    try {
      await clearIdentity();
      onSignOut();
    } catch (cause) {
      console.warn('No se pudo borrar la identidad local.', cause);
    }
  }, [onSignOut]);
  const value = useMemo(() => ({ identity, address, connection, profile, refreshProfile, signOut }), [identity, address, connection, profile, refreshProfile, signOut]);

  return (
    <SessionContext.Provider value={value}>
      {children}
      <View pointerEvents="none" style={{ height: 1, left: 0, opacity: 0, overflow: 'hidden', position: 'absolute', top: 0, width: 1 }}>
        <SessionScreen identity={identity} onAddressChange={updateAddress} onSnapshotChange={updateConnection} onSignOut={() => void signOut()} />
      </View>
    </SessionContext.Provider>
  );
}