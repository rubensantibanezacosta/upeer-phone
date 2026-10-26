import { useCallback } from 'react';

import { useSnackbar } from '@/components/snackbar';
import { canOpenAttachment, type AttachmentInfo } from '@/domain/attachment';
import { attachmentOpenMessage, openAttachment } from '@/services/attachment-open';

export function useAttachmentOpener() {
  const { showSnackbar } = useSnackbar();

  return useCallback(async (info: AttachmentInfo): Promise<void> => {
    if (!canOpenAttachment(info)) {
      showSnackbar('El archivo todavía no se ha descargado');
      return;
    }
    const message = attachmentOpenMessage(await openAttachment(info));
    if (message) showSnackbar(message);
  }, [showSnackbar]);
}
