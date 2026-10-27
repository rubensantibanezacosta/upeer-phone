export function clampPageIndex(value: number, total: number): number {
  if (!Number.isFinite(value) || total <= 0) return 0;
  return Math.min(Math.max(Math.round(value), 0), total - 1);
}

export function nextPageIndex(index: number, delta: number, total: number): number {
  return clampPageIndex(index + delta, total);
}

export function pageIndexFromOffset(offsetX: number, pageWidth: number, total: number): number {
  if (!Number.isFinite(offsetX) || !Number.isFinite(pageWidth) || pageWidth <= 0 || total <= 0) return 0;
  return clampPageIndex(offsetX / pageWidth, total);
}

export function pageOffset(index: number, pageWidth: number, total: number): number {
  if (!Number.isFinite(pageWidth) || pageWidth <= 0) return 0;
  return clampPageIndex(index, total) * pageWidth;
}

export function emojiRowWindow(offsetY: number, cell: number, rows: number, visibleRows: number): { first: number; last: number } {
  if (rows <= 0 || !Number.isFinite(offsetY) || cell <= 0) return { first: 0, last: 0 };
  const start = Math.max(0, Math.floor(offsetY / cell) - 1);
  const first = Math.min(start, Math.max(0, rows - visibleRows));
  const last = Math.min(rows, first + visibleRows + 1);
  return { first, last };
}
