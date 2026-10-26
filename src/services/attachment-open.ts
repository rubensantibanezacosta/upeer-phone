import { File } from 'expo-file-system';
import * as Sharing from 'expo-sharing';

import { attachmentUri, type AttachmentInfo } from '@/domain/attachment';

export type AttachmentOpenResult = 'opened' | 'missing' | 'unsupported' | 'not-downloaded';

export async function openAttachment(info: AttachmentInfo): Promise<AttachmentOpenResult> {
  const uri = attachmentUri(info);
  if (!uri) return 'not-downloaded';
  const file = new File(uri);
  if (!file.exists) return 'missing';
  if (!(await Sharing.isAvailableAsync())) return 'unsupported';
  try {
    await Sharing.shareAsync(file.uri, {
      mimeType: info.mimeType || undefined,
      dialogTitle: info.fileName,
    });
    return 'opened';
  } catch {
    return 'unsupported';
  }
}

export function attachmentOpenMessage(result: AttachmentOpenResult): string | null {
  if (result === 'missing') return 'El archivo ya no está en este dispositivo';
  if (result === 'unsupported') return 'No hay ninguna app para abrir este archivo';
  if (result === 'not-downloaded') return 'El archivo todavía no se ha descargado';
  return null;
}
