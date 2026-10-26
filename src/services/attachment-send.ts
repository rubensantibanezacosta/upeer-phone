import { sendFile } from '../../modules/upeer-transport/src/UPeerTransportModule';
import type { PickedAttachment } from './attachment-picker';

export type SendAttachmentInput = {
  upeerId: string;
  attachment: PickedAttachment;
  caption?: string | null;
  chatUpeerId?: string | null;
};

export function canSendAttachment(upeerId: string | undefined): boolean {
  if (!upeerId) return false;
  return !upeerId.startsWith('grp-');
}

export async function sendAttachment(input: SendAttachmentInput): Promise<string> {
  if (!canSendAttachment(input.upeerId)) throw new Error('Todavía no se pueden enviar adjuntos a un grupo');
  return sendFile({
    upeerId: input.upeerId,
    path: input.attachment.path,
    fileName: input.attachment.fileName,
    mimeType: input.attachment.mimeType,
    caption: input.caption ?? null,
    chatUpeerId: input.chatUpeerId ?? null,
  });
}

export function attachmentSendMessage(error: unknown): string {
  const detail = error instanceof Error ? error.message : String(error);
  if (detail.includes('no es conocido')) return 'Solo puedes enviar archivos a un contacto de confianza';
  if (detail.includes('no está iniciado')) return 'El transporte no está listo; espera unos segundos';
  if (detail.includes('adjuntos a un grupo')) return detail;
  return 'No se pudo enviar el archivo';
}
