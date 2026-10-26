import { describe, expect, it } from 'vitest';

import { BUBBLE_JOIN_RADIUS, BUBBLE_RADIUS, BUBBLE_TAIL_RADIUS, resolveBubbleShape } from './bubble-shape';

describe('resolveBubbleShape', () => {
  it('deja las cuatro esquinas redondeadas salvo la cola cuando el mensaje va solo', () => {
    expect(resolveBubbleShape({ isMine: true, startsGroup: true, endsGroup: true })).toEqual({
      borderTopLeftRadius: BUBBLE_RADIUS,
      borderTopRightRadius: BUBBLE_TAIL_RADIUS,
      borderBottomLeftRadius: BUBBLE_RADIUS,
      borderBottomRightRadius: BUBBLE_RADIUS,
    });
    expect(resolveBubbleShape({ isMine: false, startsGroup: true, endsGroup: true })).toEqual({
      borderTopLeftRadius: BUBBLE_TAIL_RADIUS,
      borderTopRightRadius: BUBBLE_RADIUS,
      borderBottomLeftRadius: BUBBLE_RADIUS,
      borderBottomRightRadius: BUBBLE_RADIUS,
    });
  });

  it('pega la esquina inferior al siguiente mensaje del mismo remitente', () => {
    expect(resolveBubbleShape({ isMine: true, startsGroup: true, endsGroup: false })).toEqual({
      borderTopLeftRadius: BUBBLE_RADIUS,
      borderTopRightRadius: BUBBLE_TAIL_RADIUS,
      borderBottomLeftRadius: BUBBLE_RADIUS,
      borderBottomRightRadius: BUBBLE_JOIN_RADIUS,
    });
  });

  it('pega ambas esquinas exteriores en los mensajes intermedios', () => {
    expect(resolveBubbleShape({ isMine: false, startsGroup: false, endsGroup: false })).toEqual({
      borderTopLeftRadius: BUBBLE_JOIN_RADIUS,
      borderTopRightRadius: BUBBLE_RADIUS,
      borderBottomLeftRadius: BUBBLE_JOIN_RADIUS,
      borderBottomRightRadius: BUBBLE_RADIUS,
    });
  });

  it('cierra el grupo con la esquina inferior redondeada', () => {
    expect(resolveBubbleShape({ isMine: true, startsGroup: false, endsGroup: true })).toEqual({
      borderTopLeftRadius: BUBBLE_RADIUS,
      borderTopRightRadius: BUBBLE_JOIN_RADIUS,
      borderBottomLeftRadius: BUBBLE_RADIUS,
      borderBottomRightRadius: BUBBLE_RADIUS,
    });
  });
});
