export const LOW_VOUCH_THRESHOLD = 40;

export const NEUTRAL_VOUCH_NOTE = 'Sin historial en la red';

function isScore(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value);
}

export function isLowReputation(vouchScore?: number): boolean {
  return isScore(vouchScore) && vouchScore < LOW_VOUCH_THRESHOLD;
}

export function getTrustWarning(vouchScore?: number): string | null {
  if (!isScore(vouchScore) || vouchScore >= LOW_VOUCH_THRESHOLD) return null;
  return `Baja reputación en la red (${Math.round(vouchScore)}/100). Ten cuidado.`;
}

export function getTrustAnnouncement(vouchScore?: number): string {
  const warning = getTrustWarning(vouchScore);
  return warning ? `Aviso: ${warning}` : '';
}
