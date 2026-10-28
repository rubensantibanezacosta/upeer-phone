import { useCallback } from 'react';
import { Alert } from 'react-native';

import { useSnackbar } from '@/components/snackbar';
import { flagMessage } from '@/domain/conversation-flags';
import type { ProtocolMessage } from '../../modules/upeer-transport/src/UPeerTransport.types';
import { shareChatTranscript } from '@/services/chat-export-file';
import { deleteContactAndConversation, getContacts, saveContact, setContactFavorite } from '@/storage/contact-store';
import { setConversationFlag } from '@/storage/conversation-flags-store';
import { deleteConversation, leaveGroup as leaveGroupNative } from '../../modules/upeer-transport/src/UPeerTransportModule';

type ChatOptionsParams = {
  isFavorite: boolean;
  isGroup: boolean;
  messages: ProtocolMessage[];
  muted: boolean;
  name: string;
  onChanged: () => Promise<void>;
  onClose: () => void;
  upeerId: string;
};

export type ChatOptionsApi = ReturnType<typeof useChatOptions>;

export function useChatOptions({ isFavorite, isGroup, messages, muted, name, onChanged, onClose, upeerId }: ChatOptionsParams) {
  const { showSnackbar } = useSnackbar();
  const meLabel = 'Tú';

  const report = useCallback((cause: unknown, message: string) => {
    console.warn('No se pudo completar la acción de la conversación.', cause);
    showSnackbar(message);
  }, [showSnackbar]);

  const toggleMute = useCallback(() => {
    setConversationFlag(upeerId, 'muted')
      .then(() => showSnackbar(flagMessage('muted', !muted)))
      .catch((cause: unknown) => report(cause, 'No se pudo cambiar el silencio'));
  }, [muted, report, showSnackbar, upeerId]);

  const toggleFavorite = useCallback(() => {
    setContactFavorite(upeerId, !isFavorite)
      .then(() => showSnackbar(isFavorite ? 'Quitado de Favoritos' : 'Añadido a Favoritos'))
      .catch((cause: unknown) => report(cause, 'No se pudo cambiar el favorito'));
  }, [isFavorite, report, showSnackbar, upeerId]);

  const exportChat = useCallback(() => {
    shareChatTranscript({ chatName: name, exportedAt: Date.now(), meLabel, messages })
      .then((result) => showSnackbar(result === 'shared' ? 'Chat exportado' : 'No hay ninguna app para guardar el archivo'))
      .catch((cause: unknown) => report(cause, 'No se pudo exportar el chat'));
  }, [messages, name, report, showSnackbar]);

  const clearChat = useCallback(() => {
    Alert.alert('Vaciar chat', 'Se borrarán todos los mensajes de esta conversación en este dispositivo. El chat sigue en tu lista.', [
      { text: 'Cancelar', style: 'cancel' },
      {
        text: 'Vaciar',
        style: 'destructive',
        onPress: () => {
          deleteConversation(upeerId)
            .then(() => onChanged())
            .then(() => showSnackbar('Conversación vaciada'))
            .catch((cause: unknown) => report(cause, 'No se pudo vaciar la conversación'));
        },
      },
    ]);
  }, [onChanged, report, showSnackbar, upeerId]);

  const blockContact = useCallback(() => {
    Alert.alert('Bloquear contacto', `¿Quieres bloquear a ${name}? No podrá enviarte mensajes nuevos.`, [
      { text: 'Cancelar', style: 'cancel' },
      {
        text: 'Bloquear',
        style: 'destructive',
        onPress: () => {
          getContacts()
            .then((contacts) => contacts.find((contact) => contact.upeerId === upeerId))
            .then((contact) => saveContact({ ...(contact ?? { address: '', createdAt: Date.now(), name, publicKey: null }), name: contact?.name ?? name, status: 'blocked', upeerId }))
            .then(() => onChanged())
            .then(() => showSnackbar('Contacto bloqueado'))
            .catch((cause: unknown) => report(cause, 'No se pudo bloquear el contacto'));
        },
      },
    ]);
  }, [name, onChanged, report, showSnackbar, upeerId]);

  const deleteChat = useCallback(() => {
    Alert.alert('Eliminar chat', `¿Quieres eliminar a ${name}? Se borrará también la conversación y, si vuelve a escribir, aparecerá como solicitud.`, [
      { text: 'Cancelar', style: 'cancel' },
      {
        text: 'Eliminar',
        style: 'destructive',
        onPress: () => {
          deleteContactAndConversation(upeerId)
            .then(() => {
              showSnackbar('Chat eliminado');
              onClose();
            })
            .catch((cause: unknown) => report(cause, 'No se pudo eliminar el chat'));
        },
      },
    ]);
  }, [name, onClose, report, showSnackbar, upeerId]);

  const leaveGroup = useCallback(() => {
    Alert.alert('Salir del grupo', 'Necesitarás que alguien te invite de nuevo para volver.', [
      { text: 'Cancelar', style: 'cancel' },
      {
        text: 'Salir',
        style: 'destructive',
        onPress: () => {
          leaveGroupNative(upeerId)
            .then(() => {
              showSnackbar('Has salido del grupo');
              onClose();
            })
            .catch((cause: unknown) => report(cause, 'No se pudo salir del grupo'));
        },
      },
    ]);
  }, [onClose, report, showSnackbar, upeerId]);

  return { blockContact, clearChat, deleteChat, exportChat, isGroup, leaveGroup, toggleFavorite, toggleMute };
}
