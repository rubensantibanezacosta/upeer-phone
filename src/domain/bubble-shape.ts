export const BUBBLE_RADIUS = 12;

export const BUBBLE_JOIN_RADIUS = 4;

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
  const shape: BubbleShape = {
    borderTopLeftRadius: BUBBLE_RADIUS,
    borderTopRightRadius: BUBBLE_RADIUS,
    borderBottomLeftRadius: BUBBLE_RADIUS,
    borderBottomRightRadius: BUBBLE_RADIUS,
  };
  if (startsGroup && endsGroup) return shape;
  if (isMine) {
    if (!startsGroup) shape.borderTopRightRadius = BUBBLE_JOIN_RADIUS;
    if (!endsGroup) shape.borderBottomRightRadius = BUBBLE_JOIN_RADIUS;
    return shape;
  }
  if (!startsGroup) shape.borderTopLeftRadius = BUBBLE_JOIN_RADIUS;
  if (!endsGroup) shape.borderBottomLeftRadius = BUBBLE_JOIN_RADIUS;
  return shape;
}
