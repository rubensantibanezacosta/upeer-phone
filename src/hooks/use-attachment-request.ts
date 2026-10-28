import { useCallback } from 'react';

import { useSnackbar } from '@/components/snackbar';
import type { AttachmentInfo } from '@/domain/attachment';
import { loadProfile } from '@/storage/identity-store';
import { getGroups, requestFile } from '../../modules/upeer-transport/src/UPeerTransportModule';

const MAX_GROUP_TARGETS = 8;

export async function attachmentRequestTargets(chatId: string): Promise<string[]> {
  if (!chatId) return [];
  if (!chatId.startsWith('grp-')) return [chatId];
  const [groups, profile] = await Promise.all([getGroups(), loadProfile()]);
  const group = groups.find((item) => item.groupId === chatId);
  if (!group || group.status !== 'active') return [];
  const self = profile?.upeerId.toLowerCase() ?? '';
  return group.members
    .map((member) => member.toLowerCase())
    .filter((member) => member.length > 0 && member !== self)
    .slice(0, MAX_GROUP_TARGETS);
}

export function useAttachmentRequester(chatId: string) {
  const { showSnackbar } = useSnackbar();

  return useCallback(async (info: AttachmentInfo, messageId: string): Promise<void> => {
    const fileId = info.transferId;
    if (!fileId) {
      showSnackbar('Este adjunto no se puede volver a pedir');
      return;
    }
    const targets = await attachmentRequestTargets(chatId);
    if (targets.length === 0) {
      showSnackbar('No hay ningún dispositivo disponible para pedir el archivo');
      return;
    }
    const sent = await Promise.all(targets.map((target) => requestFile(target, fileId, messageId, chatId)
      .catch((cause: unknown) => {
        console.warn('No se pudo pedir el archivo al remitente.', cause);
        return false;
      })));
    showSnackbar(sent.some(Boolean) ? 'Pidiendo el archivo al remitente…' : 'No se pudo pedir el archivo');
  }, [chatId, showSnackbar]);
}
