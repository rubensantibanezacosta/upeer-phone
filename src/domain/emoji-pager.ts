export function clampPageIndex(value: number, total: number): number {
  if (!Number.isFinite(value) || total <= 0) return 0;
  return Math.min(Math.max(Math.round(value), 0), total - 1);
}

export function nextPageIndex(index: number, delta: number, total: number): number {
  return clampPageIndex(index + delta, total);
}
