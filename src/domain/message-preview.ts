import { describeMessageContent, parseAttachmentMessage, type AttachmentKind } from './attachment';
import { parseContactCard } from './contact-card';
import { linkPreviewText } from './link-preview';
import { systemMessageText } from './system-message';

const CONTACT_CARD_LABEL = 'Tarjeta de contacto';

export function messageText(content: string): string {
  return linkPreviewText(content) ?? content;
}

export function previewIconKind(content: string): AttachmentKind | null {
  return parseAttachmentMessage(content)?.kind ?? null;
}

export function previewMessage(content: string): string {
  if (!content) return '';
  const system = systemMessageText(content);
  if (system) return system;
  const card = parseContactCard(content);
  if (card) return card.name ? `${CONTACT_CARD_LABEL}: ${card.name}` : CONTACT_CARD_LABEL;
  if (parseAttachmentMessage(content)) return describeMessageContent(content);
  return messageText(content);
}
