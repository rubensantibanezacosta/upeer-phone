import { describe, expect, it } from 'vitest';

import { activityBubbleOf } from './activity-bubble';

describe('activityBubbleOf', () => {
  it('describe la escritura', () => {
    expect(activityBubbleOf('typing')).toEqual({ kind: 'typing', label: 'Escribiendo' });
  });

  it('describe la grabación de una nota de voz', () => {
    expect(activityBubbleOf('recording')).toEqual({ kind: 'recording', label: 'Grabando nota de voz' });
  });

  it('no muestra burbuja sin actividad', () => {
    expect(activityBubbleOf('')).toBeNull();
    expect(activityBubbleOf(undefined)).toBeNull();
  });
});
