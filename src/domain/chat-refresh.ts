import type { ProtocolMessage } from '../../modules/upeer-transport/src/UPeerTransport.types';

export type ChatRefreshPayload = {
  messages: ProtocolMessage[];
  contacts: unknown[];
  conversations: unknown[];
};

export type ChatRefreshSnapshot = {
  messages: string;
  contacts: string;
  conversations: string;
};

export type ChatRefreshChange = {
  messages: boolean;
  contacts: boolean;
  conversations: boolean;
  any: boolean;
};

export function snapshotRefresh(payload: ChatRefreshPayload): ChatRefreshSnapshot {
  return {
    messages: JSON.stringify(payload.messages),
    contacts: JSON.stringify(payload.contacts),
    conversations: JSON.stringify(payload.conversations),
  };
}

export function changedSections(previous: ChatRefreshSnapshot | null, next: ChatRefreshSnapshot): ChatRefreshChange {
  const messages = previous === null || previous.messages !== next.messages;
  const contacts = previous === null || previous.contacts !== next.contacts;
  const conversations = previous === null || previous.conversations !== next.conversations;
  return { messages, contacts, conversations, any: messages || contacts || conversations };
}

export function hasUnreadIncoming(messages: ProtocolMessage[]): boolean {
  return messages.some((message) => !message.isMine && message.status === 'delivered');
}
