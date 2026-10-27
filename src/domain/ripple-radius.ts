type RippleStyle = {
  height?: number | string | null;
  width?: number | string | null;
};

const SQUARE_TOLERANCE = 0.8;

export function circularRippleRadius(style: RippleStyle | undefined): number | undefined {
  if (!style) return undefined;
  const { height, width } = style;
  if (typeof width !== 'number' || typeof height !== 'number') return undefined;
  const shortest = Math.min(width, height);
  const longest = Math.max(width, height);
  if (shortest <= 0 || shortest / longest < SQUARE_TOLERANCE) return undefined;
  return shortest / 2;
}
