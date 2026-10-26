import { useMemo, useState } from 'react';
import { ActivityIndicator, ScrollView, StyleSheet, Text as NativeText, View } from 'react-native';
import { ChevronDown, ChevronUp, Globe2, LockKeyhole, Network } from '@tamagui/lucide-icons-2';
import { Card, Paragraph, SizableText, XStack, YStack } from 'tamagui';

import { CopyField } from '@/components/copy-field';
import { SettingsSectionHeader } from '@/components/settings-section-parts';
import { NetworkMap } from '@/components/network-map';
import type { PeerInfo, PeerPoolEntry } from '../../modules/upeer-transport/src/UPeerTransport.types';
import type { PeerManagementSnapshot } from '@/domain/yggdrasil-peers';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';
import { NativeRipplePressable } from '@/components/native-ripple-pressable';

const LATENCY_MAX = 500;
const TECH_ITEMS = [
  { color: '#4ade80', label: 'Cifrado', value: 'ChaCha20-Poly1305 · extremo a extremo' },
  { color: '#60a5fa', label: 'Protocolo', value: 'Yggdrasil · red IPv6 descentralizada' },
  { color: '#a78bfa', label: 'Transporte', value: 'TCP directo entre nodos de la red' },
  { color: '#fb923c', label: 'Privacidad', value: 'Sin servidores centrales ni metadatos' },
  { color: '#22d3ee', label: 'Descubrimiento', value: 'Lista pública de peers global' },
];

export function ConnectionSettingsSection({ address, phase, peers, error, management, onCopy }: {
  address: string;
  phase: string;
  peers: PeerInfo[];
  error: string;
  management: PeerManagementSnapshot;
  onCopy: (value: string) => void;
}) {
  const theme = useTheme();
  const scale = useTextScale();
  const activePeers = useMemo(() => peers.filter((peer) => peer.Up), [peers]);
  const activeCount = activePeers.length;
  const managedPeers = management.peers;
  const [showDetails, setShowDetails] = useState(false);
  const [showAllReserve, setShowAllReserve] = useState(false);

  const connectedUris = useMemo(() => new Set(activePeers.map((peer) => peer.URI)), [activePeers]);
  const connected = useMemo(() => activePeers.map((activePeer) => managedPeers.find((peer) => peer.uri === activePeer.URI) ?? peerFromStatus(activePeer)), [activePeers, managedPeers]);
  const reserve = useMemo(() => management.reservePeers.filter((peer) => !connectedUris.has(peer.uri)), [connectedUris, management.reservePeers]);
  const latencies = connected.flatMap((peer) => peer.latencyMs === null ? [] : [peer.latencyMs]);
  const averageLatency = latencies.length ? Math.round(latencies.reduce((sum, latency) => sum + latency, 0) / latencies.length) : null;
  const countries = [...new Set(connected.map((peer) => peer.country).filter((country) => country !== 'Desconocido'))];
  const statusColor = activeCount ? '#4ade80' : phase.toLowerCase().includes('reintent') ? '#f59e0b' : theme.placeholder;
  const latencyColor = (latency: number | null) => latency === null ? '#4b5563' : latency < 100 ? '#22c55e' : latency < 250 ? '#f59e0b' : '#ef4444';

  return (
    <YStack gap={12}>
      <SettingsSectionHeader icon={<Network color={theme.accent} size={18} />} title="Conexión">Estado de tu nodo en la red: peers conectados, latencia y dirección.</SettingsSectionHeader>

      <Card backgroundColor={theme.backgroundElement} borderColor={theme.border} borderRadius={14} borderWidth={1} padding={16}>
        <YStack gap={12}>
          <XStack alignItems="center" gap={14}>
            <View style={[styles.statusIcon, { backgroundColor: `${statusColor}20` }]}>
              {activeCount ? <Network color={statusColor} size={22} /> : <ActivityIndicator color={statusColor} size="small" />}
            </View>
            <YStack flex={1} gap={3}>
              <SizableText color={theme.text} fontSize={scale(16)} fontWeight="700">{activeCount ? 'Conexión activa' : phase}</SizableText>
              <Paragraph color={theme.placeholder} fontSize={scale(12)}>{activeCount ? 'Tus mensajes viajan cifrados de extremo a extremo.' : statusSubtitle(phase)}</Paragraph>
            </YStack>
          </XStack>
          {error ? <Paragraph accessibilityRole="alert" color={theme.placeholder} fontSize={scale(12)}>{error}</Paragraph> : null}
        </YStack>
      </Card>

      {activeCount > 0 ? <>
        <View style={[styles.divider, { backgroundColor: theme.border }]} />
        <YStack gap={10} paddingHorizontal={4}>
          <XStack alignItems="center" flexWrap="wrap" gap={8}>
            <Globe2 color={theme.placeholder} size={15} />
            <SizableText color={theme.placeholder} flex={1} fontSize={scale(11)} fontWeight="700" letterSpacing={1}>CONECTADOS A LA RED · {activeCount}</SizableText>
            <XStack alignItems="center" gap={5}>
              <View style={[styles.liveDot, { backgroundColor: '#22c55e' }]} />
              <TextSmall color={theme.placeholder}>Ahora mismo</TextSmall>
            </XStack>
          </XStack>
          <Paragraph color={theme.placeholder} fontSize={scale(12)}>Conexiones actuales de tu nodo.</Paragraph>
          <XStack flexWrap="wrap" gap={7}>
            {averageLatency !== null && <View style={[styles.chip, { backgroundColor: `${latencyColor(averageLatency)}20` }]}><TextSmall color={latencyColor(averageLatency)}>Media: {averageLatency} ms · {averageLatency < 100 ? 'Excelente' : averageLatency < 250 ? 'Buena' : 'Alta'}</TextSmall></View>}
            {countries.map((country) => <View key={country} style={[styles.chip, { borderColor: theme.border, borderWidth: 1 }]}><TextSmall color={theme.textSecondary}>{country}</TextSmall></View>)}
          </XStack>
          <NetworkMap peers={managedPeers} connectedUris={connectedUris} selfLat={management.selfLat} selfLon={management.selfLon} />
          <SizableText color={theme.text} fontSize={scale(13)} fontWeight="700">Conectados ahora · {connected.length}</SizableText>
          {connected.length ? connected.map((peer) => <PeerRow key={peer.uri} peer={peer} latencyColor={latencyColor} theme={theme} />) : <Paragraph color={theme.placeholder} fontSize={scale(12)}>Sin peers públicos conectados.</Paragraph>}
          <SizableText color={theme.text} fontSize={scale(13)} fontWeight="700">Peers públicos en reserva · {reserve.length}</SizableText>
          {reserve.length ? <>
            <ScrollView horizontal nestedScrollEnabled showsHorizontalScrollIndicator={false}>
              <XStack gap={8}>
                {(showAllReserve ? reserve : reserve.slice(0, 5)).map((peer) => <View key={peer.uri} style={{ width: 228 }}>
                  <Card backgroundColor={theme.backgroundElement} borderColor={theme.border} borderRadius={10} borderWidth={1} padding={10}>
                    <YStack gap={4}><SizableText color={theme.text} fontSize={scale(12)} fontWeight="600">{peer.country}</SizableText><TextSmall color={theme.placeholder}>{peer.host}</TextSmall><TextSmall color={theme.placeholder}>{peer.uri}</TextSmall><TextSmall color={theme.placeholder}>{peer.alive ? `${peer.latencyMs ?? '—'} ms · score ${Math.round(peer.score)}` : 'Sin respuesta'}</TextSmall></YStack>
                  </Card>
                </View>)}
              </XStack>
            </ScrollView>
            {reserve.length > 5 ? <NativeRipplePressable accessibilityRole="button" onPress={() => setShowAllReserve((shown) => !shown)}><SizableText color={theme.accent} fontSize={scale(12)}>{showAllReserve ? 'Mostrar menos' : `Ver todos (${reserve.length})`}</SizableText></NativeRipplePressable> : null}
          </> : <Paragraph color={theme.placeholder} fontSize={scale(12)}>No hay peers públicos de reserva.</Paragraph>}
        </YStack>
      </> : null}

      {!management.statusAvailable ? <Paragraph color={theme.placeholder} fontSize={scale(12)}>Aún no se han podido consultar los datos actuales del pool móvil.</Paragraph> : null}

      {activeCount > 0 && address ? <>
        <View style={[styles.divider, { backgroundColor: theme.border }]} />
        <YStack gap={8} paddingHorizontal={4}>
          <SizableText color={theme.text} fontSize={scale(14)} fontWeight="700">Tu dirección de red</SizableText>
          <CopyField accessibilityLabel="Copiar dirección de red" onCopy={() => onCopy(address)} value={address} />
        </YStack>
      </> : null}

      <View style={[styles.divider, { backgroundColor: theme.border }]} />
      <NativeRipplePressable accessibilityRole="button" accessibilityState={{ expanded: showDetails }} onPress={() => setShowDetails((shown) => !shown)} style={styles.detailsToggle}>
        <XStack alignItems="center" gap={8}><LockKeyhole color={theme.placeholder} size={15} /><SizableText color={theme.placeholder} fontSize={scale(12)} fontWeight="600">Cómo funciona la red</SizableText></XStack>
        {showDetails ? <ChevronUp color={theme.placeholder} size={18} /> : <ChevronDown color={theme.placeholder} size={18} />}
      </NativeRipplePressable>
      {showDetails ? <YStack gap={4} paddingHorizontal={4}>
        {TECH_ITEMS.map((item) => <XStack key={item.label} alignItems="flex-start" gap={12} paddingHorizontal={8} paddingVertical={8}>
          <View style={[styles.techIcon, { backgroundColor: `${item.color}20` }]}><Network color={item.color} size={16} /></View>
          <YStack flex={1} gap={2}><SizableText color={theme.text} fontSize={scale(12)} fontWeight="700">{item.label}</SizableText><Paragraph color={theme.placeholder} fontSize={scale(12)}>{item.value}</Paragraph></YStack>
        </XStack>)}
      </YStack> : null}
    </YStack>
  );
}

function PeerRow({ peer, latencyColor, theme }: { peer: PeerPoolEntry; latencyColor: (latency: number | null) => string; theme: ReturnType<typeof useTheme> }) {
  const scale = useTextScale();
  const latency = peer.latencyMs;
  const color = latencyColor(latency);
  const width = `${Math.min(((latency ?? 0) / LATENCY_MAX) * 100, 100)}%` as `${number}%`;
  return <Card backgroundColor={theme.backgroundElement} borderColor={theme.border} borderRadius={10} borderWidth={1} padding={10}>
    <YStack gap={8}>
      <XStack alignItems="center" gap={10}>
        <YStack flex={1} gap={2}>
          <SizableText color={theme.text} fontSize={scale(12)} fontWeight="600">{peer.country}</SizableText>
          <TextSmall color={theme.placeholder}>{peer.host}</TextSmall>
          <TextSmall color={theme.placeholder}>{peer.uri}</TextSmall>
        </YStack>
        <TextSmall color={latency !== null ? color : theme.placeholder}>{latency !== null ? `${Math.round(latency)} ms` : '—'}</TextSmall>
      </XStack>
      <View style={[styles.latencyTrack, { backgroundColor: theme.backgroundSelected }]}><View style={[styles.latencyBar, { backgroundColor: color, width }]} /></View>
    </YStack>
  </Card>;
}

function TextSmall({ children, color }: { children: React.ReactNode; color: string }) {
  const scale = useTextScale();
  return <NativeText numberOfLines={2} style={{ color, fontSize: scale(10), lineHeight: scale(14) }}>{children}</NativeText>;
}

function statusSubtitle(phase: string): string {
  if (phase.toLowerCase().includes('reintent')) return 'Intentando restaurar la conexión con la red.';
  if (phase.toLowerCase().includes('no se pudo') || phase.toLowerCase().includes('sin peer')) return 'No hay conexión disponible. Comprueba tu conexión a Internet.';
  return 'Un momento, estamos conectando tu app a la red segura.';
}

function peerFromStatus(peer: PeerInfo): PeerPoolEntry {
  let host = peer.URI;
  try {
    host = new URL(peer.URI).hostname;
  } catch {
    host = peer.URI;
  }
  return {
    uri: peer.URI,
    host,
    country: 'Desconocido',
    uptimePct: peer.Uptime,
    latencyMs: peer.Latency > 0 ? peer.Latency / 1_000_000 : 0,
    score: 0,
    alive: true,
    distanceKm: null,
    lat: null,
    lon: null,
  };
}

const styles = StyleSheet.create({
  statusIcon: { alignItems: 'center', borderRadius: 10, height: 44, justifyContent: 'center', width: 44 },
  divider: { height: StyleSheet.hairlineWidth, marginHorizontal: 4, marginVertical: 4 },
  liveDot: { borderRadius: 99, height: 7, width: 7 },
  chip: { alignItems: 'center', borderRadius: 99, paddingHorizontal: 9, paddingVertical: 5 },
  detailsToggle: { alignItems: 'center', flexDirection: 'row', justifyContent: 'space-between', minHeight: 42, paddingHorizontal: 8 },
  techIcon: { alignItems: 'center', borderRadius: 7, height: 30, justifyContent: 'center', width: 30 },
  latencyTrack: { borderRadius: 99, height: 6, overflow: 'hidden' },
  latencyBar: { borderRadius: 99, height: '100%' },
});