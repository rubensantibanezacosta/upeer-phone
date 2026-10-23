export type Identifiable = {
  upeerId: string;
};

export type ConversationCandidate = Identifiable & {
  alias?: string;
  preview: string;
  timestamp: number;
};

export type ContactCandidate = {
  upeerId: string;
  status: string;
};

export type ConversationPartition<T extends Identifiable> = {
  accepted: T[];
  requests: T[];
  hidden: T[];
};

export function classifyConversations<T extends Identifiable>(conversations: T[], contacts: ContactCandidate[]): ConversationPartition<T> {
  const byId = new Map(contacts.map((contact) => [contact.upeerId.toLowerCase(), contact]));
  const accepted: T[] = [];
  const requests: T[] = [];
  const hidden: T[] = [];

  for (const conversation of conversations) {
    const contact = byId.get(conversation.upeerId.toLowerCase());
    if (!contact) {
      requests.push(conversation);
      continue;
    }
    if (contact.status === 'blocked') {
      hidden.push(conversation);
      continue;
    }
    accepted.push(conversation);
  }

  return { accepted, hidden, requests };
}

export function requestLabel(conversation: Identifiable & { alias?: string }): string {
  const alias = conversation.alias?.trim();
  if (alias) return alias;
  return conversation.upeerId.slice(0, 12);
}
