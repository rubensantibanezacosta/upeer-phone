import { File } from 'expo-file-system';
import * as Sharing from 'expo-sharing';

import { attachmentUri, canOpenAttachment, type AttachmentInfo } from '@/domain/attachment';

export type AttachmentOpenResult = 'opened' | 'missing' | 'unsupported' | 'not-downloaded';

export type AttachmentOpenPlan =
  | { kind: 'media' }
  | { kind: 'share' }
  | { kind: 'request' }
  | { kind: 'own-devices' }
  | { kind: 'notice'; message: string };

export const OWN_DEVICE_NOTICE = 'El archivo está en otro de tus dispositivos';

export function planAttachmentOpen(
  info: AttachmentInfo,
  options: { exists: boolean; requestable: boolean; isMine?: boolean }
): AttachmentOpenPlan {
  const request = (message: string): AttachmentOpenPlan => {
    if (options.isMine) return { kind: 'own-devices' };
    return options.requestable ? { kind: 'request' } : { kind: 'notice', message };
  };
  if (!canOpenAttachment(info)) return request('El archivo todavía no se ha descargado');
  if (options.exists) {
    if (info.kind === 'image' || info.kind === 'video') return { kind: 'media' };
    return { kind: 'share' };
  }
  return request('El archivo ya no está en este dispositivo');
}

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
