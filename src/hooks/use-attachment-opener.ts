import { useCallback } from 'react';

import { useSnackbar } from '@/components/snackbar';
import { attachmentUri, canOpenAttachment, type AttachmentInfo } from '@/domain/attachment';
import { attachmentFileExists } from '@/hooks/use-attachment-file';
import { attachmentOpenMessage, openAttachment } from '@/services/attachment-open';

export function useAttachmentOpener(onMedia?: (info: AttachmentInfo) => void) {
  const { showSnackbar } = useSnackbar();

  return useCallback(async (info: AttachmentInfo): Promise<void> => {
    if (!canOpenAttachment(info)) {
      showSnackbar('El archivo todavía no se ha descargado');
      return;
    }
    if (info.kind === 'image' || info.kind === 'video') {
      const uri = attachmentUri(info);
      if (!uri || !attachmentFileExists(uri)) {
        showSnackbar('El archivo ya no está en este dispositivo');
        return;
      }
      onMedia?.(info);
      return;
    }
    const message = attachmentOpenMessage(await openAttachment(info));
    if (message) showSnackbar(message);
  }, [onMedia, showSnackbar]);
}
