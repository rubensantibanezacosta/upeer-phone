import type { AppearanceFontSize } from './preferences';

const MIN_SIZE = 9;

export const TEXT_SCALE_FACTORS: Record<AppearanceFontSize, number> = {
  small: 0.9,
  medium: 1,
  large: 1.18,
};

export function scaleFontSize(size: number, option: AppearanceFontSize): number {
  const factor = TEXT_SCALE_FACTORS[option] ?? TEXT_SCALE_FACTORS.medium;
  return Math.max(MIN_SIZE, Math.round(size * factor));
}

export function describeFontScale(option: AppearanceFontSize): string {
  const percent = Math.round(TEXT_SCALE_FACTORS[option] * 100);
  return percent === 100 ? 'Tamaño normal' : `Texto al ${percent} %`;
}
