import { useMemo, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import Svg, { Circle, G, Line, Path, Text as SvgText } from 'react-native-svg';
import { geoNaturalEarth1, geoPath } from 'd3-geo';
import { feature } from 'topojson-client';
import type { FeatureCollection } from 'geojson';
import type { GeometryObject, Topology } from 'topojson-specification';
import worldAtlas from 'world-atlas/countries-110m.json';

import type { PeerPoolEntry } from '../../modules/upeer-transport/src/UPeerTransport.types';
import { COUNTRY_CENTROIDS } from '@/domain/country-centroids';
import { useTheme } from '@/hooks/use-theme';

const MAP_WIDTH = 800;
const MAP_HEIGHT = 450;
const MAX_MAP_PEERS = 40;
const LEGEND = [
  { color: '#22c55e', label: '< 100 ms' },
  { color: '#f59e0b', label: '100–250 ms' },
  { color: '#ef4444', label: '> 250 ms' },
  { color: '#4b5563', label: 'Sin respuesta' },
];

const topology = worldAtlas as unknown as Topology;
const countries = feature(topology, topology.objects['countries'] as GeometryObject) as unknown as FeatureCollection;
const projection = geoNaturalEarth1()
  .scale(235)
  .translate([MAP_WIDTH / 2, MAP_HEIGHT / 2])
  .center([10, 48]);
const pathGenerator = geoPath(projection);
const COUNTRY_PATHS = countries.features.flatMap((country, index) => {
  const path = pathGenerator(country);
  if (!path) return [];
  const name = typeof country.properties?.name === 'string' ? country.properties.name : `country-${index}`;
  return [{ key: `${name}-${index}`, path }];
});

function latencyColor(peer: PeerPoolEntry): string {
  if (!peer.alive || peer.latencyMs === null) return '#4b5563';
  if (peer.latencyMs < 100) return '#22c55e';
  if (peer.latencyMs < 250) return '#f59e0b';
  return '#ef4444';
}

function projectPoint(lat: number, lon: number): { x: number; y: number } | null {
  const point = projection([lon, lat]);
  return point ? { x: point[0], y: point[1] } : null;
}

export function NetworkMap({ peers, connectedUris, selfLat, selfLon }: {
  peers: PeerPoolEntry[];
  connectedUris: Set<string>;
  selfLat: number | null;
  selfLon: number | null;
}) {
  const theme = useTheme();
  const [selectedUri, setSelectedUri] = useState<string | null>(null);
  const points = useMemo(() => peers.flatMap((peer) => {
    const centroid = COUNTRY_CENTROIDS[peer.country];
    const lat = peer.lat ?? centroid?.[1] ?? null;
    const lon = peer.lon ?? centroid?.[0] ?? null;
    if (lat === null || lon === null) return [];
    const projected = projectPoint(lat, lon);
    if (!projected) return [];
    return [{
      peer,
      ...projected,
      color: connectedUris.has(peer.uri) ? latencyColor(peer) : '#38bdf8',
    }];
  }).slice(0, MAX_MAP_PEERS), [connectedUris, peers]);
  const self = selfLat !== null && selfLon !== null ? projectPoint(selfLat, selfLon) : null;
  const selected = selectedUri ? points.find((point) => point.peer.uri === selectedUri)?.peer ?? null : null;

  return (
    <View style={[styles.card, { borderColor: theme.border }]}>
      <View style={styles.canvas}>
        <Svg width="100%" height="100%" viewBox={`0 0 ${MAP_WIDTH} ${MAP_HEIGHT}`} preserveAspectRatio="xMidYMid meet">
          <G>
            {COUNTRY_PATHS.map(({ key, path }) => (
              <Path key={key} d={path} fill="#0f2135" stroke="#1e3a5f" strokeLinejoin="round" strokeWidth={0.6} />
            ))}
          </G>
          {self ? points.map(({ peer, x, y, color }) => (
            <Line
              key={`line-${peer.uri}`}
              x1={self.x}
              y1={self.y}
              x2={x}
              y2={y}
              stroke={color}
              strokeDasharray={peer.alive ? undefined : [4, 3]}
              strokeLinecap="round"
              strokeOpacity={peer.alive ? 0.55 : 0.22}
              strokeWidth={peer.alive ? 2 : 1.2}
            />
          )) : null}
          {points.map(({ peer, x, y, color }) => (
            <G key={peer.uri} onPress={() => setSelectedUri((current) => current === peer.uri ? null : peer.uri)}>
              <Circle cx={x} cy={y} r={12} fill="transparent" />
              <Circle cx={x} cy={y} r={5} fill={color} fillOpacity={0.95} stroke={color} strokeOpacity={0.35} strokeWidth={1.4} />
            </G>
          ))}
          {self ? (
            <G>
              <Circle cx={self.x} cy={self.y} r={8} fill="#fff" fillOpacity={0.95} stroke="#93c5fd" strokeWidth={2.2} />
              <SvgText x={self.x} y={self.y - 14} fill="#fff" fillOpacity={0.8} fontFamily="monospace" fontSize={20} fontWeight="700" textAnchor="middle">Tú</SvgText>
            </G>
          ) : null}
        </Svg>
        <View pointerEvents="none" style={styles.caption}>
          <Text style={styles.captionText}>VISTA DE RED · {points.length}/{peers.length} NODOS EN MAPA</Text>
        </View>
        {selected ? (
          <View pointerEvents="none" style={[styles.tooltip, { borderColor: latencyColor(selected) }]}>
            <Text style={[styles.tooltipCountry, { color: latencyColor(selected) }]}>{selected.country || 'Desconocido'}</Text>
            <Text style={styles.tooltipMeta}>{selected.latencyMs === null ? 'Sin latencia' : `${Math.round(selected.latencyMs)} ms`} · score {Math.round(selected.score)}</Text>
            {selected.distanceKm !== null ? <Text style={styles.tooltipMeta}>{Math.round(selected.distanceKm)} km</Text> : null}
            <Text numberOfLines={1} style={styles.tooltipHost}>{selected.host}</Text>
          </View>
        ) : null}
      </View>
      <View style={styles.legend}>
        {LEGEND.map(({ color, label }) => (
          <View key={label} style={styles.legendItem}>
            <View style={[styles.legendDot, { backgroundColor: color }]} />
            <Text style={[styles.legendText, { color: theme.placeholder }]}>{label}</Text>
          </View>
        ))}
        <View style={styles.legendSpacer} />
        <View style={styles.legendItem}>
          <View style={[styles.legendDot, styles.selfDot]} />
          <Text style={[styles.legendText, { color: theme.placeholder }]}>Tu posición</Text>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  card: { backgroundColor: '#060e1a', borderRadius: 12, borderWidth: 1, overflow: 'hidden' },
  canvas: { height: 210, position: 'relative' },
  caption: { bottom: 8, left: 10, position: 'absolute' },
  captionText: { color: '#94a3b8', fontSize: 10, lineHeight: 14 },
  tooltip: { backgroundColor: '#0b1929', borderRadius: 8, borderWidth: 1, left: 8, maxWidth: 190, paddingHorizontal: 9, paddingVertical: 7, position: 'absolute', top: 8 },
  tooltipCountry: { fontSize: 11, fontWeight: '700' },
  tooltipMeta: { color: '#94a3b8', fontSize: 10, marginTop: 2 },
  tooltipHost: { color: '#64748b', fontFamily: 'monospace', fontSize: 9, marginTop: 3 },
  legend: { alignItems: 'center', borderTopColor: '#1e3a5f', borderTopWidth: StyleSheet.hairlineWidth, flexDirection: 'row', flexWrap: 'wrap', gap: 10, paddingHorizontal: 10, paddingVertical: 8 },
  legendItem: { alignItems: 'center', flexDirection: 'row', gap: 4 },
  legendDot: { borderRadius: 2, height: 7, width: 7 },
  selfDot: { backgroundColor: '#fff', opacity: 0.85 },
  legendText: { fontSize: 9 },
  legendSpacer: { flex: 1 },
});
