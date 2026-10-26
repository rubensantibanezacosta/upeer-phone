import { describe, expect, it } from 'vitest';

import { BUBBLE_JOIN_RADIUS, BUBBLE_RADIUS, resolveBubbleShape } from './bubble-shape';

const allRounded = {
  borderTopLeftRadius: BUBBLE_RADIUS,
  borderTopRightRadius: BUBBLE_RADIUS,
  borderBottomLeftRadius: BUBBLE_RADIUS,
  borderBottomRightRadius: BUBBLE_RADIUS,
};

describe('resolveBubbleShape', () => {
  it('redondea las cuatro esquinas cuando el mensaje va solo', () => {
    expect(resolveBubbleShape({ isMine: true, startsGroup: true, endsGroup: true })).toEqual(allRounded);
    expect(resolveBubbleShape({ isMine: false, startsGroup: true, endsGroup: true })).toEqual(allRounded);
  });

  it('afila la esquina inferior derecha en el primer mensaje propio de una cadena', () => {
    expect(resolveBubbleShape({ isMine: true, startsGroup: true, endsGroup: false })).toEqual({
      ...allRounded,
      borderBottomRightRadius: BUBBLE_JOIN_RADIUS,
    });
  });

  it('afila la esquina superior derecha en el último mensaje propio de una cadena', () => {
    expect(resolveBubbleShape({ isMine: true, startsGroup: false, endsGroup: true })).toEqual({
      ...allRounded,
      borderTopRightRadius: BUBBLE_JOIN_RADIUS,
    });
  });

  it('afila las dos esquinas derechas en los mensajes propios intermedios', () => {
    expect(resolveBubbleShape({ isMine: true, startsGroup: false, endsGroup: false })).toEqual({
      ...allRounded,
      borderTopRightRadius: BUBBLE_JOIN_RADIUS,
      borderBottomRightRadius: BUBBLE_JOIN_RADIUS,
    });
  });

  it('afila el lado izquierdo en las cadenas del otro usuario', () => {
    expect(resolveBubbleShape({ isMine: false, startsGroup: true, endsGroup: false })).toEqual({
      ...allRounded,
      borderBottomLeftRadius: BUBBLE_JOIN_RADIUS,
    });
    expect(resolveBubbleShape({ isMine: false, startsGroup: false, endsGroup: true })).toEqual({
      ...allRounded,
      borderTopLeftRadius: BUBBLE_JOIN_RADIUS,
    });
    expect(resolveBubbleShape({ isMine: false, startsGroup: false, endsGroup: false })).toEqual({
      ...allRounded,
      borderTopLeftRadius: BUBBLE_JOIN_RADIUS,
      borderBottomLeftRadius: BUBBLE_JOIN_RADIUS,
    });
  });
});
