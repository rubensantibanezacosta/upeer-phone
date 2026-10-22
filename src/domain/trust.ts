// Placeholder hasta que el roadmap móvil integre reputación; 50 replica el estado neutro de escritorio.
export const NEUTRAL_VOUCH_SCORE = 50;

export type TrustKind = 'alert' | 'verified' | 'check' | 'new' | 'shield';

export type TrustDescriptor = {
  kind: TrustKind;
  label: string;
};

export function getTrustDescriptor(vouchScore = NEUTRAL_VOUCH_SCORE): TrustDescriptor {
  if (vouchScore < 40) return { kind: 'alert', label: `Baja reputación (${vouchScore}/100), ten cuidado` };
  if (vouchScore >= 80) return { kind: 'verified', label: `Alta reputación (${vouchScore}/100), muy confiable` };
  if (vouchScore >= 65) return { kind: 'check', label: `Buena reputación (${vouchScore}/100), confiable` };
  if (vouchScore === NEUTRAL_VOUCH_SCORE) return { kind: 'new', label: 'Sin historial de red aún' };
  return { kind: 'shield', label: `Reputación estándar (${vouchScore}/100)` };
}

export function getTrustLabel(vouchScore?: number): string {
  return getTrustDescriptor(vouchScore).label;
}
