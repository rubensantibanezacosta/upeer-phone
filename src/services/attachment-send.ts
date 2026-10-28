import { sendFile } from '../../modules/upeer-transport/src/UPeerTransportModule';
import type { PickedAttachment } from './attachment-picker';

export type SendAttachmentInput = {
  upeerId: string;
  attachment: PickedAttachment;
  caption?: string | null;
  chatUpeerId?: string | null;
  isVoiceNote?: boolean;
};

export function canSendAttachment(upeerId: string | undefined): boolean {
  return Boolean(upeerId);
}

export async function sendAttachment(input: SendAttachmentInput): Promise<string> {
  if (!canSendAttachment(input.upeerId)) throw new Error('No hay una conversación a la que enviar el archivo');
  return sendFile({
    upeerId: input.upeerId,
    path: input.attachment.path,
    fileName: input.attachment.fileName,
    mimeType: input.attachment.mimeType,
    caption: input.caption ?? null,
    chatUpeerId: input.chatUpeerId ?? null,
    isVoiceNote: input.isVoiceNote === true,
  });
}

export function attachmentSendMessage(error: unknown): string {
  const detail = error instanceof Error ? error.message : String(error);
  if (detail.includes('no es conocido')) return 'Solo puedes enviar archivos a un contacto de confianza';
  if (detail.includes('no está iniciado')) return 'El transporte no está listo; espera unos segundos';
  if (detail.includes('grupo no está disponible')) return 'El grupo ya no está disponible en este dispositivo';
  if (detail.includes('no tiene miembros')) return 'No hay miembros del grupo a los que enviar el archivo';
  return 'No se pudo enviar el archivo';
}
