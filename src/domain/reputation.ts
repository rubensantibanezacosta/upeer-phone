export type ReputationScores = Record<string, number>;

export const MIN_SCORE = 0;
export const MAX_SCORE = 100;

export function parseReputationScores(raw: unknown): ReputationScores {
  if (typeof raw !== 'string' || raw.trim() === '') return {};
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    return {};
  }
  if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) return {};
  const scores: ReputationScores = {};
  for (const [key, value] of Object.entries(parsed as Record<string, unknown>)) {
    const id = key.trim().toLowerCase();
    if (id === '') continue;
    if (typeof value !== 'number' || !Number.isFinite(value)) continue;
    scores[id] = Math.min(MAX_SCORE, Math.max(MIN_SCORE, Math.round(value)));
  }
  return scores;
}

export function scoreOf(scores: ReputationScores, upeerId?: string | null): number | undefined {
  if (!upeerId) return undefined;
  return scores[upeerId.trim().toLowerCase()];
}
