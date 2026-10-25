import { useEffect, useEffectEvent, useRef, useState } from 'react';
import { ActivityIndicator, AppState, PermissionsAndroid, Platform, ScrollView, View } from 'react-native';
import { Card, H1, Paragraph, SizableText, Text, XStack, YStack } from 'tamagui';

import { createPeerManagementSnapshot, EMPTY_PEER_MANAGEMENT, type PeerManagementSnapshot } from '@/domain/yggdrasil-peers';
import { getConnectedPeerCount, getNodeInfo, getPeerPool, getPeerStatus, retryPeers, setTransportForeground, startWithPeers } from '../../modules/upeer-transport/src/UPeerTransportModule';
import type { PeerPoolSnapshot } from '../../modules/upeer-transport/src/UPeerTransportModule';
import type { PeerInfo } from '../../modules/upeer-transport/src/UPeerTransport.types';
import type { UPeerIdentity } from '@/domain/identity-record';
import { loadOrCreateYggdrasilSeed, loadProfile, replaceYggdrasilSeed } from '@/storage/identity-store';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';
import { NativeRippleButton } from '@/components/native-ripple-button';

type SessionScreenProps = { identity: UPeerIdentity; onAddressChange: (address: string) => void; onSignOut: () => void };
export type ConnectionSnapshot = { address: string; phase: string; peers: PeerInfo[]; error: string; management: PeerManagementSnapshot };
const STATUS_REFRESH_INTERVAL_MS = 5_000;

export function SessionScreen({ identity, onAddressChange, onSnapshotChange, onSignOut }: SessionScreenProps & { onSnapshotChange: (snapshot: ConnectionSnapshot) => void }) {
  const theme = useTheme();
  const scale = useTextScale();
  const [alias, setAlias] = useState('');
  const [address, setAddress] = useState('');
  const [peers, setPeers] = useState<PeerInfo[]>([]);
  const [management, setManagement] = useState(EMPTY_PEER_MANAGEMENT);
  const [phase, setPhase] = useState('Conectando a la red…');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [starting, setStarting] = useState(true);
  const [nodeStarted, setNodeStarted] = useState(false);
  const reconnecting = useRef(false);
  const nodeStartedRef = useRef(false);

  const refreshStatus = async (): Promise<boolean> => {
    try {
      const [status, count, pool] = await Promise.all([getPeerStatus(), getConnectedPeerCount(), getPeerPool()]);
      setPeers(status);
      setManagement(peerPoolSnapshot(status, pool));
      const inbound = status.filter((peer) => peer.Inbound).length;
      setPhase(count > 0 ? `Conectado · ${count} peers activos${inbound ? ` · ${inbound} entrante${inbound === 1 ? '' : 's'}` : ''}` : nodeStarted ? 'Buscando nodos de la red…' : 'Iniciando conexión segura…');
      if (count > 0) setError('');
      return count > 0;
    } catch (cause) {
      setPhase('Conexión interrumpida');
      setError(errorMessage(cause));
      return false;
    }
  };

  const connect = async (initial = false, scheduleReconnect = true): Promise<boolean> => {
    setBusy(true);
    setError('');
    setPhase('Iniciando conexión segura…');
    try {
      if (Platform.OS === 'android' && Number(Platform.Version) >= 33) {
        const permission = await PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS);
        if (permission !== PermissionsAndroid.RESULTS.GRANTED) {
          throw new Error('Activa las notificaciones para mantener visible el servicio de conexión en segundo plano.');
        }
      }
      await setTransportForeground();
      if (initial) {
        const existingNode = await getNodeInfo();
        if (existingNode) {
          nodeStartedRef.current = true;
          setNodeStarted(true);
          setAddress(existingNode.address);
          await refreshStatus();
          return true;
        }
        nodeStartedRef.current = false;
        setNodeStarted(false);
      }
      if (!initial) {
        await retryPeers();
        await refreshStatus();
        return true;
      }
      let nodeSeed = await loadOrCreateYggdrasilSeed();
      let node;
      try {
        node = await startWithPeers([], nodeSeed);
      } catch (cause) {
        if (!isKeyMismatch(cause) || nodeStartedRef.current) throw cause;
        nodeSeed.fill(0);
        nodeSeed = await replaceYggdrasilSeed();
        node = await startWithPeers([], nodeSeed);
      } finally {
        nodeSeed.fill(0);
      }
      nodeStartedRef.current = true;
      setNodeStarted(true);
      setAddress(node.address);
      setPhase('Nodo activo · el servicio está seleccionando peers');
      await refreshStatus();
      return true;
    } catch (cause) {
      setPhase(nodeStarted ? 'Conexión inestable · reintentando' : 'No se pudo iniciar la conexión');
      setError(errorMessage(cause));
      if (scheduleReconnect) void autoReconnect();
      return false;
    } finally {
      setBusy(false);
    }
  };

  const autoReconnect = async () => {
    if (reconnecting.current) return;
    reconnecting.current = true;
    try {
      for (let attempt = 0; attempt < 3; attempt += 1) {
        try {
          let canCheckPeers = nodeStartedRef.current;
          if (!nodeStartedRef.current) {
            const started = await connect(true, false);
            canCheckPeers = started && nodeStartedRef.current;
          } else if (attempt > 0) {
            await connect(false, false);
          }
          if (canCheckPeers) {
            const count = await getConnectedPeerCount();
            if (count > 0) {
              await refreshStatus();
              return;
            }
          }
        } catch (cause) {
          setError(errorMessage(cause));
        }
        if (nodeStartedRef.current) {
          try {
            await retryPeers();
          } catch (cause) {
            setError(errorMessage(cause));
          }
        }
        await new Promise((resolve) => setTimeout(resolve, 5_000 * (attempt + 1)));
      }
      setPhase('Sin peers activos · se seguirá reintentando');
    } finally {
      reconnecting.current = false;
    }
  };

  const signOut = async () => {
    setBusy(true);
    try {
      await onSignOut();
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setBusy(false);
    }
  };

  const notifyAddressChange = useEffectEvent((nextAddress: string) => onAddressChange(nextAddress));
  const monitorNetwork = useEffectEvent(async () => {
    const connected = await refreshStatus();
    if (!connected) {
      await autoReconnect();
      return;
    }
  });
  const startSession = useEffectEvent(async () => connect(true));

  useEffect(() => {
    let active = true;
    let monitor: ReturnType<typeof setInterval> | undefined;
    void loadProfile().then((profile) => { if (active) setAlias(profile?.alias || 'Mi cuenta'); }).catch((cause: unknown) => { if (active) setError(errorMessage(cause)); });
    const startTimer = setTimeout(() => {
      void startSession().finally(() => {
        if (active) {
          setStarting(false);
          monitor = setInterval(() => void monitorNetwork(), STATUS_REFRESH_INTERVAL_MS);
        }
      });
    }, 0);
    const appStateSubscription = AppState.addEventListener('change', (state) => {
      if (state === 'active') {
        void setTransportForeground().catch((cause: unknown) => setError(errorMessage(cause)));
        void monitorNetwork();
      }
    });
    return () => {
      active = false;
      clearTimeout(startTimer);
      if (monitor) clearInterval(monitor);
      appStateSubscription.remove();
    };
  }, []);

  useEffect(() => {
    if (address) notifyAddressChange(address);
  }, [address]);

  useEffect(() => {
    onSnapshotChange({ address, phase, peers, error, management });
  }, [address, error, management, onSnapshotChange, peers, phase]);

  return (
    <View style={{ flex: 1, backgroundColor: theme.background }}>
      <ScrollView contentContainerStyle={{ alignSelf: 'center', gap: 18, paddingVertical: 12, width: '100%', maxWidth: 600 }}>
        <YStack gap={6} paddingVertical={10}>
          <SizableText color={theme.accent} fontSize={scale(13)} fontWeight="700" letterSpacing={2}>UPEER</SizableText>
          <H1 color={theme.text} fontSize={scale(24)}>Conexión</H1>
          <SizableText color={theme.text} fontSize={scale(18)} fontWeight="600">{alias}</SizableText>
          <Text color={theme.placeholder} style={{ fontFamily: 'monospace' }} fontSize={scale(11)}>{identity.upeerId}</Text>
        </YStack>
        <Card backgroundColor={theme.backgroundElement} borderColor={theme.border} borderRadius={18} borderWidth={1} padding={18}>
          <YStack gap={12}>
            <XStack alignItems="center" gap={10}>
              {busy && <ActivityIndicator size="small" />}
              <SizableText color={peers.some((peer) => peer.Up) ? theme.accent : theme.text} fontSize={scale(17)} fontWeight="700">{phase}</SizableText>
            </XStack>
            {address ? <Text color={theme.placeholder} style={{ fontFamily: 'monospace' }} fontSize={scale(12)}>{address}</Text> : <Text color={theme.placeholder} fontSize={scale(12)}>Dirección de red: esperando conexión…</Text>}
          </YStack>
        </Card>
        <YStack gap={10}>
          <SizableText color={theme.text} fontSize={scale(17)} fontWeight="700">Nodos de la red</SizableText>
          {peers.length === 0 ? <Card backgroundColor={theme.backgroundElement} borderColor={theme.border} borderRadius={14} borderWidth={1} padding={16}><Paragraph color={theme.placeholder}>{starting ? 'Conectando automáticamente a la red…' : 'Buscando peers automáticamente…'}</Paragraph></Card> : peers.map((peer, index) => (
            <Card key={`${peer.URI}-${index}`} backgroundColor={theme.backgroundElement} borderColor={theme.border} borderRadius={14} borderWidth={1} padding={14}>
              <XStack alignItems="center" gap={10}>
                <YStack backgroundColor={peer.Up ? theme.accent : theme.placeholder} borderRadius={99} height={9} width={9} />
                <YStack flex={1} gap={3}>
                  <SizableText color={theme.text} fontSize={scale(13)} fontWeight="600">{peer.URI}</SizableText>
                  <Paragraph color={theme.placeholder} fontSize={scale(12)}>{peer.Up ? `Activo · ${(peer.Latency / 1_000_000).toFixed(0)} ms` : peer.LastError || 'Conectando'}</Paragraph>
                </YStack>
              </XStack>
            </Card>
          ))}
        </YStack>
        {!!error && <Paragraph accessibilityRole="alert" color={theme.placeholder}>{error} · Se reintentará automáticamente.</Paragraph>}
        <NativeRippleButton backgroundColor="transparent" borderColor={theme.border} borderWidth={1} color="danger" disabled={busy} height={46} onPress={() => void signOut()}>Cerrar sesión y borrar datos locales</NativeRippleButton>
      </ScrollView>
    </View>
  );
}

function rawMessage(cause: unknown): string {
  return cause instanceof Error ? cause.message : '';
}

function errorMessage(cause: unknown): string {
  const message = rawMessage(cause);
  if (!message) return 'No se pudo conectar a la red.';
  if (/yggdrasil|yggstack/i.test(message)) return 'No se pudo completar la conexión con la red.';
  return message;
}

function isKeyMismatch(cause: unknown): boolean {
  const message = rawMessage(cause).toLowerCase();
  return message.includes('key') && (message.includes('does not match') || message.includes('mismatch') || message.includes('certificate'));
}

function peerPoolSnapshot(peers: PeerInfo[], snapshot: PeerPoolSnapshot): PeerManagementSnapshot {
  const activeUris = new Set(peers.filter((peer) => peer.Up).map((peer) => peer.URI));
  const activePool = snapshot.peers.filter((peer) => activeUris.has(peer.uri));
  return createPeerManagementSnapshot(activePool, snapshot.peers.filter((peer) => !activeUris.has(peer.uri)), snapshot.selfLat, snapshot.selfLon);
}