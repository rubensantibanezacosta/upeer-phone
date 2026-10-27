import * as VideoThumbnails from 'expo-video-thumbnails';

export type ThumbnailRequest = {
  time?: number;
  quality?: number;
};

const CACHE = new Map<string, Promise<string | null>>();

export function thumbnailKey(uri: string, request: ThumbnailRequest = {}): string {
  return `${uri}@${request.time ?? 500}@${request.quality ?? 0.6}`;
}

async function generate(uri: string, request: ThumbnailRequest): Promise<string | null> {
  try {
    const result = await VideoThumbnails.getThumbnailAsync(uri, { quality: request.quality ?? 0.6, time: request.time ?? 500 });
    return result.uri || null;
  } catch (cause) {
    console.warn('No se pudo generar la miniatura del vídeo.', cause);
    return null;
  }
}

export function videoThumbnail(uri: string | null | undefined, request: ThumbnailRequest = {}): Promise<string | null> {
  if (!uri) return Promise.resolve(null);
  const key = thumbnailKey(uri, request);
  const cached = CACHE.get(key);
  if (cached) return cached;
  const pending = generate(uri, request).then((result) => {
    if (!result) CACHE.delete(key);
    return result;
  });
  CACHE.set(key, pending);
  return pending;
}

export function clearThumbnailCache(): void {
  CACHE.clear();
}
