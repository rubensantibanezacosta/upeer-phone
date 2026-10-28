import { useMemo } from 'react';
import { File } from 'expo-file-system';

export function attachmentFileExists(uri: string | null): boolean {
  if (!uri) return false;
  try {
    return new File(uri).exists;
  } catch (cause) {
    console.warn('No se pudo comprobar si el adjunto está en el dispositivo.', cause);
    return false;
  }
}

export function useAttachmentFile(uri: string | null): boolean {
  return useMemo(() => attachmentFileExists(uri), [uri]);
}
