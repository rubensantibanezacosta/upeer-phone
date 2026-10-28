import { describe, expect, it } from 'vitest';

import { describeUnsupported, hasUnsupported } from './sync-unsupported';

describe('describeUnsupported', () => {
  it('dice que está todo cuando no falta nada', () => {
    expect(describeUnsupported(0)).toBe('Todo el contenido de tus dispositivos está replicado.');
    expect(describeUnsupported(-3)).toBe('Todo el contenido de tus dispositivos está replicado.');
  });

  it('nombra el tipo real en singular y plural', () => {
    expect(describeUnsupported(1, { attachment: 1 })).toBe('1 adjunto recibido por sincronización que aún no está en este dispositivo.');
    expect(describeUnsupported(3, { attachment: 3 })).toBe('3 adjuntos recibidos por sincronización que aún no están en este dispositivo.');
  });

  it('no nombra tipos con cero', () => {
    expect(describeUnsupported(2, { attachment: 2, group: 0 })).toBe('2 adjuntos recibidos por sincronización que aún no están en este dispositivo.');
  });

  it('separa varios tipos con y', () => {
    expect(describeUnsupported(3, { attachment: 2, group: 1 })).toContain('2 adjuntos');
    expect(describeUnsupported(3, { attachment: 2, group: 1 })).toContain(' y 1 elemento sin contenido completo');
  });

  it('cae a un recuento genérico sin desglose', () => {
    expect(describeUnsupported(1)).toBe('1 elemento sin contenido completo en este dispositivo.');
    expect(describeUnsupported(4, {})).toBe('4 elementos sin contenido completo en este dispositivo.');
  });

  it('no afirma nada sobre grupos, que ya se replican', () => {
    expect(describeUnsupported(2, { attachment: 2 })).not.toContain('grupo');
  });
});

describe('hasUnsupported', () => {
  it('solo avisa con algo pendiente', () => {
    expect(hasUnsupported(1)).toBe(true);
    expect(hasUnsupported(0)).toBe(false);
  });
});
