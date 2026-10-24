import { ChatForwardSheet } from '@/components/chat/chat-forward-sheet';
import { ChatMessageActions } from '@/components/chat/chat-message-actions';
import { summarizeReactions } from '@/domain/chat-reactions';
import type { ChatMessageSheetApi } from '@/hooks/use-chat-message-sheet';
import type { UPeerContact } from '@/storage/contact-store';
import type { ProtocolMessage } from '../../../modules/upeer-transport/src/UPeerTransport.types';

type ChatMessageOverlaysProps = {
  contacts: UPeerContact[];
  forwarding: ProtocolMessage | null;
  onForwardClose: () => void;
  onForwarded: (count: number, failed: number) => void;
  selfUpeerId: string;
  sheet: ChatMessageSheetApi;
};

export function ChatMessageOverlays({ contacts, forwarding, onForwardClose, onForwarded, selfUpeerId, sheet }: ChatMessageOverlaysProps) {
  return (
    <>
      <ChatMessageActions
        canEdit={sheet.selected?.isMine === true}
        canRetry={sheet.selected?.isMine === true && sheet.selected.status === 'failed'}
        message={sheet.selected}
        onClose={sheet.close}
        onCopy={sheet.copyMessage}
        onDelete={sheet.remove}
        onEdit={sheet.editMessage}
        onForward={sheet.forwardMessage}
        onReply={sheet.replyMessage}
        onRetry={sheet.retryMessage}
        onToggleReaction={sheet.toggleReaction}
        reactions={summarizeReactions(sheet.selected?.reactions, selfUpeerId)}
      />
      {forwarding ? (
        <ChatForwardSheet
          contacts={contacts}
          key={forwarding.id}
          message={forwarding}
          onClose={onForwardClose}
          onForwarded={onForwarded}
        />
      ) : null}
    </>
  );
}
