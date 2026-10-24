export type ReactionSummary = {
  emoji: string;
  count: number;
  mine: boolean;
};

export const QUICK_REACTIONS = ['👍', '❤️', '😂', '😮', '😢', '🙏'] as const;

export function summarizeReactions(
  reactions: Record<string, string[]> | undefined,
  selfUpeerId: string,
): ReactionSummary[] {
  if (!reactions) return [];
  return Object.entries(reactions)
    .filter(([, actors]) => Array.isArray(actors) && actors.length > 0)
    .map(([emoji, actors]) => ({
      emoji,
      count: actors.length,
      mine: actors.some((actor) => actor.toLowerCase() === selfUpeerId.toLowerCase()),
    }));
}

export function isReactedByMe(
  reactions: Record<string, string[]> | undefined,
  emoji: string,
  selfUpeerId: string,
): boolean {
  const actors = reactions?.[emoji];
  if (!actors) return false;
  return actors.some((actor) => actor.toLowerCase() === selfUpeerId.toLowerCase());
}
