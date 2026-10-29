import { previewMessage } from './message-preview';

export type LatestReaction = {
  emoji: string;
  message: string;
};

export function reactionPreview(reaction: LatestReaction | undefined | null): string | null {
  if (!reaction) return null;
  const emoji = reaction.emoji?.trim();
  const text = previewMessage(reaction.message ?? '');
  if (!emoji && !text) return null;
  if (!emoji) return text;
  if (!text) return emoji;
  return `${emoji} ${text}`;
}
