export const BUBBLE_RADIUS = 12;

export const BUBBLE_TAIL_RADIUS = 4;

export const BUBBLE_JOIN_RADIUS = 6;

export type BubbleShapeInput = {
  isMine: boolean;
  startsGroup: boolean;
  endsGroup: boolean;
};

export type BubbleShape = {
  borderTopLeftRadius: number;
  borderTopRightRadius: number;
  borderBottomLeftRadius: number;
  borderBottomRightRadius: number;
};

export function resolveBubbleShape({ isMine, startsGroup, endsGroup }: BubbleShapeInput): BubbleShape {
  const outerTop = startsGroup ? BUBBLE_TAIL_RADIUS : BUBBLE_JOIN_RADIUS;
  const outerBottom = endsGroup ? BUBBLE_RADIUS : BUBBLE_JOIN_RADIUS;
  if (isMine) {
    return {
      borderTopLeftRadius: BUBBLE_RADIUS,
      borderTopRightRadius: outerTop,
      borderBottomLeftRadius: BUBBLE_RADIUS,
      borderBottomRightRadius: outerBottom,
    };
  }
  return {
    borderTopLeftRadius: outerTop,
    borderTopRightRadius: BUBBLE_RADIUS,
    borderBottomLeftRadius: outerBottom,
    borderBottomRightRadius: BUBBLE_RADIUS,
  };
}
