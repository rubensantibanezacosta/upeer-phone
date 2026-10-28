import { useCallback, useState } from 'react';
import { Alert } from 'react-native';

import { useSnackbar } from '@/components/snackbar';
import { pickAttachment, pickResultMessage, type PickSource, type PickedAttachment } from '@/services/attachment-picker';
import { attachmentSendMessage, canSendAttachment, sendAttachment } from '@/services/attachment-send';

const SOURCES: { label: string; source: PickSource }[] = [
  { label: 'Galería', source: 'library' },
  { label: 'Documento', source: 'document' },
];

export function useAttachmentSender(upeerId: string | undefined, onSent?: () => void) {
  const { showSnackbar } = useSnackbar();
  const [cameraOpen, setCameraOpen] = useState(false);

  const sendPicked = useCallback(async (attachment: PickedAttachment, isVoiceNote = false) => {
    if (!canSendAttachment(upeerId)) {
      showSnackbar('No hay una conversación abierta');
      return;
    }
    try {
      await sendAttachment({ attachment, isVoiceNote, upeerId: upeerId as string });
      showSnackbar(`Enviando ${attachment.fileName}`);
      onSent?.();
    } catch (cause) {
      console.warn('No se pudo enviar el adjunto.', cause);
      showSnackbar(attachmentSendMessage(cause));
    }
  }, [onSent, showSnackbar, upeerId]);

  const send = useCallback(async (source: PickSource) => {
    if (!canSendAttachment(upeerId)) {
      showSnackbar('No hay una conversación abierta');
      return;
    }
    const result = await pickAttachment(source);
    const message = pickResultMessage(result);
    if (message) {
      showSnackbar(message);
      return;
    }
    if (result.status !== 'picked') return;
    await sendPicked(result.attachment);
  }, [sendPicked, showSnackbar, upeerId]);

  const attach = useCallback(() => {
    if (!canSendAttachment(upeerId)) {
      showSnackbar('No hay una conversación abierta');
      return;
    }
    Alert.alert('Adjuntar archivo', 'Elige de dónde tomar el archivo', [
      ...SOURCES.map(({ label, source }) => ({ onPress: () => { void send(source); }, text: label })),
      { style: 'cancel' as const, text: 'Cancelar' },
    ]);
  }, [send, showSnackbar, upeerId]);

  const closeCamera = useCallback(() => setCameraOpen(false), []);

  const openLibrary = useCallback(() => {
    void send('library').then(() => setCameraOpen(false));
  }, [send]);

  const attachFromCamera = useCallback(() => {
    if (!canSendAttachment(upeerId)) {
      showSnackbar('No hay una conversación abierta');
      return;
    }
    setCameraOpen(true);
  }, [showSnackbar, upeerId]);

  const sendVoiceNote = useCallback(async (attachment: PickedAttachment) => {
    await sendPicked(attachment, true);
  }, [sendPicked]);

  return { attach, attachFromCamera, cameraOpen, closeCamera, openLibrary, sendPicked, sendVoiceNote };
}
