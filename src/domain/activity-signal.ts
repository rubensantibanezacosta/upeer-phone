export const ACTIVITY_THROTTLE_MS = 15_000;
export const ACTIVITY_HEARTBEAT_MS = 40_000;

export type ActivityTrigger = 'resume' | 'gesture' | 'heartbeat';

export function shouldReportActivity(trigger: ActivityTrigger, lastReportedAt: number, now: number): boolean {
  if (trigger === 'resume') return true;
  if (!Number.isFinite(lastReportedAt) || lastReportedAt <= 0) return true;
  const elapsed = now - lastReportedAt;
  if (elapsed < 0) return true;
  return elapsed >= (trigger === 'heartbeat' ? ACTIVITY_HEARTBEAT_MS : ACTIVITY_THROTTLE_MS);
}
