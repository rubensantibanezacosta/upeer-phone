import { Asset, AssetField, MediaType, Query, getPermissionsAsync, requestPermissionsAsync } from 'expo-media-library';

import { guessMimeType, pickedAttachment, type PickedAttachment } from '@/services/attachment-picker';

export const RECENT_MEDIA_LIMIT = 12;

export type RecentMediaKind = 'image' | 'video';

export type RecentMediaItem = {
  duration: number | null;
  id: string;
  kind: RecentMediaKind;
  title: string;
  uri: string;
};

type MediaMetadata = {
  duration: number | null;
  filename: string | null;
  id: string;
  mediaType: MediaType;
};

type RecentMediaCandidate = Omit<RecentMediaItem, 'uri'>;

export type RecentMediaPermission = 'granted' | 'denied';

export function mediaKind(mediaType: MediaType): RecentMediaKind | null {
  if (mediaType === MediaType.IMAGE) return 'image';
  if (mediaType === MediaType.VIDEO) return 'video';
  return null;
}

export function formatDuration(seconds: number | null | undefined): string {
  if (!seconds || seconds <= 0) return '';
  const total = Math.round(seconds);
  const minutes = Math.floor(total / 60);
  const rest = total % 60;
  return `${minutes}:${String(rest).padStart(2, '0')}`;
}

export function toRecentMediaItem(metadata: MediaMetadata): RecentMediaCandidate | null {
  const kind = mediaKind(metadata.mediaType);
  if (!kind || !metadata.id) return null;
  return {
    duration: kind === 'video' ? metadata.duration : null,
    id: metadata.id,
    kind,
    title: metadata.filename ?? (kind === 'video' ? 'Vídeo' : 'Foto'),
  };
}

export function recentMediaItems(metadata: MediaMetadata[]): RecentMediaCandidate[] {
  const seen = new Set<string>();
  const items: RecentMediaCandidate[] = [];
  for (const entry of metadata) {
    const item = toRecentMediaItem(entry);
    if (!item || seen.has(item.id)) continue;
    seen.add(item.id);
    items.push(item);
  }
  return items;
}

export async function ensureMediaPermission(): Promise<RecentMediaPermission> {
  const current = await getPermissionsAsync(false, ['photo', 'video']);
  if (current.granted) return 'granted';
  const requested = await requestPermissionsAsync(false, ['photo', 'video']);
  return requested.granted ? 'granted' : 'denied';
}

export async function resolveRecentMediaUri(id: string): Promise<string> {
  return new Asset(id).getUri();
}

async function withUri(candidate: RecentMediaCandidate): Promise<RecentMediaItem | null> {
  try {
    return { ...candidate, uri: await resolveRecentMediaUri(candidate.id) };
  } catch (cause) {
    console.warn('No se pudo resolver la miniatura de una foto reciente.', cause);
    return null;
  }
}

export async function loadRecentMedia(limit = RECENT_MEDIA_LIMIT): Promise<RecentMediaItem[]> {
  const permission = await ensureMediaPermission();
  if (permission === 'denied') return [];
  const metadata = await new Query()
    .within(AssetField.MEDIA_TYPE, [MediaType.IMAGE, MediaType.VIDEO])
    .orderBy({ ascending: false, key: AssetField.CREATION_TIME })
    .limit(limit)
    .exeForMetadata();
  const resolved = await Promise.all(recentMediaItems(metadata as MediaMetadata[]).map(withUri));
  return resolved.filter((item): item is RecentMediaItem => item !== null);
}

export async function recentMediaAttachment(item: RecentMediaItem): Promise<PickedAttachment> {
  const fileName = item.title.trim() || (item.kind === 'video' ? 'video.mp4' : 'foto.jpg');
  return pickedAttachment(item.uri, fileName, guessMimeType(fileName));
}
