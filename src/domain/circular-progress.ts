export type RingGeometry = {
  center: number;
  radius: number;
  circumference: number;
  dashOffset: number;
  percent: number;
};

export const INDETERMINATE_PERCENT = 25;

export function clampPercent(value: number | undefined): number {
  if (typeof value !== 'number' || Number.isNaN(value)) return 0;
  return Math.min(100, Math.max(0, value));
}

export function ringGeometry(size: number, thickness: number, value: number | undefined): RingGeometry {
  const safeSize = size > 0 ? size : 0;
  const safeThickness = Math.min(Math.max(thickness, 0), safeSize / 2);
  const center = safeSize / 2;
  const radius = Math.max(0, center - safeThickness / 2);
  const circumference = 2 * Math.PI * radius;
  const percent = clampPercent(value);
  return {
    center,
    radius,
    circumference,
    dashOffset: circumference - (percent * circumference) / 100,
    percent,
  };
}
