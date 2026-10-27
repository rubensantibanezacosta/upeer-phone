import { File, Paths } from 'expo-file-system';
import { ImageManipulator, SaveFormat } from 'expo-image-manipulator';

import { parseLinkPreviewHtml } from '@/domain/link-preview-html';
import type { LinkPreview } from '@/domain/link-preview';

const TIMEOUT_MS = 3500;
const MAX_HTML_CHARS = 128 * 1024;
const MIN_IMAGE_BYTES = 512;
const TARGET_IMAGE_BYTES = 18 * 1024;
const IMAGE_SIDES = [320, 240, 160];
const IMAGE_QUALITIES = [0.6, 0.45, 0.3];
const BROWSER_HEADERS = {
  Accept: 'text/html,application/xhtml+xml,*/*',
  'User-Agent': 'Twitterbot/1.0',
};

export async function fetchLinkPreview(url: string, signal?: AbortSignal): Promise<LinkPreview | null> {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), TIMEOUT_MS);
  const abort = () => controller.abort();
  signal?.addEventListener('abort', abort);
  try {
    const response = await fetch(url, { headers: BROWSER_HEADERS, signal: controller.signal });
    if (!response.ok) return null;
    const type = response.headers.get('content-type') ?? '';
    if (!type.includes('html')) return null;
    const html = (await response.text()).slice(0, MAX_HTML_CHARS);
    const parsed = parseLinkPreviewHtml(html, url);
    if (!parsed) return null;
    const image = await firstImage(parsed.images, controller.signal);
    return image ? { ...parsed.preview, imageBase64: image } : parsed.preview;
  } catch (cause) {
    if (!controller.signal.aborted) console.warn('No se pudo leer la vista previa del enlace.', cause);
    return null;
  } finally {
    clearTimeout(timeout);
    signal?.removeEventListener('abort', abort);
  }
}

async function firstImage(candidates: string[], signal: AbortSignal): Promise<string | undefined> {
  for (const candidate of candidates) {
    if (signal.aborted) return undefined;
    const image = await downloadImage(candidate, signal);
    if (image) return image;
  }
  return undefined;
}

async function downloadImage(url: string, signal: AbortSignal): Promise<string | undefined> {
  const target = new File(Paths.cache, `upeer-preview-${Date.now()}-${Math.random().toString(36).slice(2)}`);
  try {
    const file = await File.downloadFileAsync(url, target);
    if ((file.info().size ?? 0) < MIN_IMAGE_BYTES) return undefined;
    return await shrink(file);
  } catch (cause) {
    if (!signal.aborted) console.warn('No se pudo descargar la imagen de la vista previa.', cause);
    return undefined;
  } finally {
    remove(target);
  }
}

async function shrink(file: File): Promise<string | undefined> {
  let best: string | undefined;
  for (const side of IMAGE_SIDES) {
    for (const quality of IMAGE_QUALITIES) {
      const encoded = await encode(file, side, quality);
      if (!encoded) continue;
      if (!best || encoded.length < best.length) best = encoded;
      if (encoded.length * 0.75 <= TARGET_IMAGE_BYTES) return encoded;
    }
  }
  return best;
}

async function encode(file: File, side: number, quality: number): Promise<string | undefined> {
  const context = ImageManipulator.manipulate(file.uri, { maxWidth: side });
  try {
    const image = await context.resize({ width: side }).renderAsync();
    try {
      const saved = await image.saveAsync({ base64: true, compress: quality, format: SaveFormat.WEBP });
      remove(new File(saved.uri));
      if (!saved.base64) return undefined;
      return `data:image/webp;base64,${saved.base64}`;
    } finally {
      image.release();
    }
  } catch (cause) {
    console.warn('No se pudo reducir la imagen de la vista previa.', cause);
    return undefined;
  } finally {
    context.release();
  }
}

function remove(file: File) {
  try {
    if (file.exists) file.delete();
  } catch (cause) {
    console.warn('No se pudo borrar el archivo temporal de la vista previa.', cause);
  }
}
