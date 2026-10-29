import { parseAttachmentMessage, type AttachmentKind } from './attachment';
import { parseContactCard } from './contact-card';
import { systemMessageText } from './system-message';
import { messageText } from './message-preview';

const TEXT_LIMIT = 64;
const FILE_LIMIT = 48;

export type LatestReaction = {
  emoji: string;
  message: string;
  actorName?: string;
  actorIsMe?: boolean;
};

function compact(value: string, limit: number): string {
  const text = value.replace(/\s+/g, ' ').trim();
  return text.length <= limit ? text : `${text.slice(0, limit).trimEnd()}…`;
}

function quoted(value: string, limit: number): string {
  const text = compact(value, limit);
  return text ? `"${text}"` : '';
}

function describeAttachment(kind: AttachmentKind, fileName: string): string {
  const name = quoted(fileName, FILE_LIMIT);
  if (kind === 'voice') return 'a la nota de voz';
  if (kind === 'image') return name ? `a la imagen ${name}` : 'a una imagen';
  if (kind === 'video') return name ? `al vídeo ${name}` : 'a un vídeo';
  return name ? `al archivo ${name}` : 'a un archivo';
}

export function describeReactedContent(content: string): string {
  if (!content) return '';
  const attachment = parseAttachmentMessage(content);
  if (attachment) return describeAttachment(attachment.kind, attachment.fileName);
  const system = systemMessageText(content);
  if (system) return `al aviso ${quoted(system, TEXT_LIMIT)}`;
  if (parseContactCard(content)) return 'a una tarjeta de contacto';
  const text = quoted(messageText(content), TEXT_LIMIT);
  return text ? `al mensaje ${text}` : '';
}

export type ReactionActor = {
  actorName: string;
  actorIsMe: boolean;
};

export function reactionActor(
  actorUpeerId: string | undefined,
  names: ReadonlyMap<string, string>,
  myUPeerId: string | undefined,
  myAlias?: string | null,
): ReactionActor {
  const actor = (actorUpeerId ?? '').toLowerCase();
  if (actor && myUPeerId && actor === myUPeerId.toLowerCase()) {
    return { actorName: (myAlias ?? '').trim(), actorIsMe: true };
  }
  return { actorName: names.get(actor) ?? '', actorIsMe: false };
}

export function reactionPreview(reaction: LatestReaction | undefined | null): string | null {
  if (!reaction) return null;
  const emoji = reaction.emoji?.trim();
  const actorName = reaction.actorName?.trim() || 'Alguien';
  const actor = reaction.actorIsMe ? 'Reaccionaste' : `${actorName} reaccionó`;
  const stated = emoji ? `${actor} con ${emoji}` : actor;
  const content = describeReactedContent(reaction.message ?? '');
  if (!content && !emoji) return null;
  return content ? `${stated} ${content}` : stated;
}
