import { useCallback } from 'react';

import { useSnackbar } from '@/components/snackbar';
import { attachmentUri, type AttachmentInfo } from '@/domain/attachment';
import { attachmentFileExists } from '@/hooks/use-attachment-file';
import { attachmentOpenMessage, openAttachment, OWN_DEVICE_NOTICE, planAttachmentOpen } from '@/services/attachment-open';

export function useAttachmentOpener(
  onMedia?: (info: AttachmentInfo) => void,
  onMissing?: (info: AttachmentInfo, messageId: string) => void | Promise<void>
) {
  const { showSnackbar } = useSnackbar();

  return useCallback(async (info: AttachmentInfo, messageId = '', isMine = false): Promise<void> => {
    const uri = attachmentUri(info);
    const plan = planAttachmentOpen(info, {
      exists: uri ? attachmentFileExists(uri) : false,
      requestable: Boolean(onMissing),
      isMine,
    });
    if (plan.kind === 'request') {
      void onMissing?.(info, messageId);
      return;
    }
    if (plan.kind === 'notice') {
      showSnackbar(plan.message);
      return;
    }
    if (plan.kind === 'media') {
      onMedia?.(info);
      return;
    }
    const result = await openAttachment(info);
    if (result === 'missing' && onMissing && !isMine) {
      void onMissing(info, messageId);
      return;
    }
    const message = result === 'missing' ? OWN_DEVICE_NOTICE : attachmentOpenMessage(result);
    if (message) showSnackbar(message);
  }, [onMedia, onMissing, showSnackbar]);
}
