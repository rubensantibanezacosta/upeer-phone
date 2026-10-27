import { useCallback } from 'react';
import { Alert } from 'react-native';

import { useSnackbar } from '@/components/snackbar';
import { pickAttachment, pickResultMessage, type PickSource } from '@/services/attachment-picker';
import { attachmentSendMessage, canSendAttachment, sendAttachment } from '@/services/attachment-send';

const SOURCES: { label: string; source: PickSource }[] = [
  { label: 'Galería', source: 'library' },
  { label: 'Cámara', source: 'camera' },
  { label: 'Documento', source: 'document' },
];

export function useAttachmentSender(upeerId: string | undefined, onSent?: () => void) {
  const { showSnackbar } = useSnackbar();

  const send = useCallback(async (source: PickSource) => {
    if (!canSendAttachment(upeerId)) {
      showSnackbar('Todavía no se pueden enviar adjuntos a un grupo');
      return;
    }
    const result = await pickAttachment(source);
    const message = pickResultMessage(result);
    if (message) {
      showSnackbar(message);
      return;
    }
    if (result.status !== 'picked') return;
    try {
      await sendAttachment({ attachment: result.attachment, upeerId: upeerId as string });
      showSnackbar(`Enviando ${result.attachment.fileName}`);
      onSent?.();
    } catch (cause) {
      console.warn('No se pudo enviar el adjunto.', cause);
      showSnackbar(attachmentSendMessage(cause));
    }
  }, [onSent, showSnackbar, upeerId]);

  const attach = useCallback(() => {
    if (!canSendAttachment(upeerId)) {
      showSnackbar('Todavía no se pueden enviar adjuntos a un grupo');
      return;
    }
    Alert.alert('Adjuntar archivo', 'Elige de dónde tomar el archivo', [
      ...SOURCES.map(({ label, source }) => ({ onPress: () => { void send(source); }, text: label })),
      { style: 'cancel' as const, text: 'Cancelar' },
    ]);
  }, [send, showSnackbar, upeerId]);

  const attachFromCamera = useCallback(() => {
    void send('camera');
  }, [send]);

  return { attach, attachFromCamera };
}
