import * as DocumentPicker from 'expo-document-picker';
import * as ImagePicker from 'expo-image-picker';

export type PickSource = 'library' | 'camera' | 'document';

export type PickedAttachment = {
  path: string;
  fileName: string;
  mimeType: string;
};

export type PickResult =
  | { status: 'picked'; attachment: PickedAttachment }
  | { status: 'cancelled' }
  | { status: 'denied' }
  | { status: 'failed' };

const MIME_BY_EXTENSION: Record<string, string> = {
  avif: 'image/avif',
  bmp: 'image/bmp',
  gif: 'image/gif',
  heic: 'image/heic',
  jpeg: 'image/jpeg',
  jpg: 'image/jpeg',
  m4a: 'audio/mp4',
  mkv: 'video/x-matroska',
  mov: 'video/quicktime',
  mp3: 'audio/mpeg',
  mp4: 'video/mp4',
  ogg: 'audio/ogg',
  pdf: 'application/pdf',
  png: 'image/png',
  txt: 'text/plain',
  webm: 'video/webm',
  webp: 'image/webp',
};

export function fileNameOf(uri: string, fallback = 'archivo'): string {
  const withoutQuery = uri.split('?')[0];
  const name = withoutQuery.split('/').pop() ?? '';
  const decoded = decodeURIComponent(name).trim();
  return decoded.length > 0 ? decoded : fallback;
}

export function guessMimeType(fileName: string): string {
  const dot = fileName.lastIndexOf('.');
  if (dot < 0) return 'application/octet-stream';
  return MIME_BY_EXTENSION[fileName.slice(dot + 1).toLowerCase()] ?? 'application/octet-stream';
}

export function toFilePath(uri: string): string {
  return uri.startsWith('file://') ? decodeURIComponent(uri.slice('file://'.length)) : uri;
}

export function pickedAttachment(uri: string, fileName?: string | null, mimeType?: string | null): PickedAttachment {
  const name = fileName?.trim() || fileNameOf(uri);
  const mime = mimeType?.trim() || guessMimeType(name);
  return { path: toFilePath(uri), fileName: name, mimeType: mime };
}

async function fromImagePicker(result: ImagePicker.ImagePickerResult): Promise<PickResult> {
  if (result.canceled) return { status: 'cancelled' };
  const asset = result.assets?.[0];
  if (!asset?.uri) return { status: 'failed' };
  return { status: 'picked', attachment: pickedAttachment(asset.uri, asset.fileName, asset.mimeType) };
}

async function pickFromCamera(): Promise<PickResult> {
  const permission = await ImagePicker.requestCameraPermissionsAsync();
  if (!permission.granted) return { status: 'denied' };
  return fromImagePicker(await ImagePicker.launchCameraAsync({ mediaTypes: ['images', 'videos'], quality: 1 }));
}

async function pickFromLibrary(): Promise<PickResult> {
  const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
  if (!permission.granted) return { status: 'denied' };
  return fromImagePicker(await ImagePicker.launchImageLibraryAsync({ mediaTypes: ['images', 'videos'], quality: 1 }));
}

async function pickFromDocuments(): Promise<PickResult> {
  const result = await DocumentPicker.getDocumentAsync({ copyToCacheDirectory: true, multiple: false });
  if (result.canceled) return { status: 'cancelled' };
  const asset = result.assets?.[0];
  if (!asset?.uri) return { status: 'failed' };
  return { status: 'picked', attachment: pickedAttachment(asset.uri, asset.name, asset.mimeType) };
}

export async function pickAttachment(source: PickSource): Promise<PickResult> {
  try {
    if (source === 'camera') return await pickFromCamera();
    if (source === 'library') return await pickFromLibrary();
    return await pickFromDocuments();
  } catch (cause) {
    console.warn('No se pudo abrir el selector de archivos.', cause);
    return { status: 'failed' };
  }
}

export function pickResultMessage(result: PickResult): string | null {
  if (result.status === 'denied') return 'Necesitas dar permiso para adjuntar ese archivo';
  if (result.status === 'failed') return 'No se pudo leer el archivo seleccionado';
  return null;
}
