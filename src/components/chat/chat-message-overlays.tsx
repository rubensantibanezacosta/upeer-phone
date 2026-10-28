import { ChatForwardSheet } from '@/components/chat/chat-forward-sheet';
import { ChatMessageActions } from '@/components/chat/chat-message-actions';
import { summarizeReactions } from '@/domain/chat-reactions';
import type { ChatMessageSheetApi } from '@/hooks/use-chat-message-sheet';
import type { UPeerContact } from '@/storage/contact-store';
import type { ProtocolMessage } from '../../../modules/upeer-transport/src/UPeerTransport.types';

type ChatMessageOverlaysProps = {
  contacts: UPeerContact[];
  forwarding: ProtocolMessage[] | null;
  onForwardClose: () => void;
  onForwarded: (count: number, failed: number) => void;
  onSelect: (message: ProtocolMessage) => void;
  selfUpeerId: string;
  sheet: ChatMessageSheetApi;
};

export function ChatMessageOverlays({ contacts, forwarding, onForwardClose, onForwarded, onSelect, selfUpeerId, sheet }: ChatMessageOverlaysProps) {
  return (
    <>
      <ChatMessageActions
        canEdit={sheet.selected?.isMine === true && sheet.selected.deleted !== true}
        canForward={sheet.selected?.deleted !== true}
        canRetry={sheet.selected?.isMine === true && sheet.selected.deleted !== true && sheet.selected.status === 'failed'}
        message={sheet.selected}
        onClose={sheet.close}
        onCopy={sheet.copyMessage}
        onDelete={sheet.remove}
        onEdit={sheet.editMessage}
        onForward={sheet.forwardMessage}
        onReply={sheet.replyMessage}
        onRetry={sheet.retryMessage}
        onSelect={onSelect}
        onToggleReaction={sheet.toggleReaction}
        onToggleStar={sheet.toggleStar}
        reactions={summarizeReactions(sheet.selected?.reactions, selfUpeerId)}
        starred={sheet.isStarred}
      />
      {forwarding ? (
        <ChatForwardSheet
          contacts={contacts}
          key={forwarding.map((message) => message.id).join('-')}
          messages={forwarding}
          onClose={onForwardClose}
          onForwarded={onForwarded}
        />
      ) : null}
    </>
  );
}
