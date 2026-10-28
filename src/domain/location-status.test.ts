import { describe, expect, it } from 'vitest';

import { describeAge, describeLocationAddress, describePublication } from './location-status';
import type { DhtLocationPublication } from '../../modules/upeer-transport/src/UPeerTransport.types';

const MINUTE = 60 * 1000;
const HOUR = 60 * MINUTE;

const publication = (extra: Partial<DhtLocationPublication> = {}): DhtLocationPublication => ({
  publishedAt: 0,
  dhtSeq: 0,
  nodes: 0,
  fanoutAt: 0,
  fanout: 0,
  error: null,
  ...extra,
});

describe('describeAge', () => {
  const now = 100 * HOUR;

  it('ignores empty timestamps', () => {
    expect(describeAge(0, now)).toBeNull();
    expect(describeAge(Number.NaN, now)).toBeNull();
  });

  it('describes minutes, hours and days', () => {
    expect(describeAge(now - 30 * 1000, now)).toBe('hace menos de un minuto');
    expect(describeAge(now - MINUTE, now)).toBe('hace 1 minuto');
    expect(describeAge(now - 5 * MINUTE, now)).toBe('hace 5 minutos');
    expect(describeAge(now - HOUR, now)).toBe('hace 1 hora');
    expect(describeAge(now - 3 * HOUR, now)).toBe('hace 3 horas');
    expect(describeAge(now - 26 * HOUR, now)).toBe('hace 1 día');
    expect(describeAge(now - 3 * 24 * HOUR, now)).toBe('hace 3 días');
  });

  it('treats a future stamp as now', () => {
    expect(describeAge(now + MINUTE, now)).toBe('ahora mismo');
  });
});

describe('describePublication', () => {
  const now = 100 * HOUR;

  it('asks for data when there is nothing yet', () => {
    const summary = describePublication(null, now);

    expect(summary.tone).toBe('idle');
    expect(summary.title).toBe('Sin datos de publicación');
  });

  it('warns when nothing has been published', () => {
    const summary = describePublication(publication(), now);

    expect(summary.tone).toBe('warn');
    expect(summary.title).toBe('Sin publicar');
  });

  it('reports a successful publication with nodes and fan-out', () => {
    const summary = describePublication(publication({ publishedAt: now - 2 * MINUTE, nodes: 4, fanout: 3 }), now);

    expect(summary.tone).toBe('ok');
    expect(summary.title).toBe('Ubicación publicada');
    expect(summary.detail).toContain('hace 2 minutos');
    expect(summary.detail).toContain('4 nodos');
    expect(summary.detail).toContain('3 contactos');
  });

  it('uses the singular where it applies', () => {
    const summary = describePublication(publication({ publishedAt: now - MINUTE, nodes: 1, fanout: 1 }), now);

    expect(summary.detail).toContain('1 nodo');
    expect(summary.detail).toContain('1 contacto');
  });

  it('says when there is nobody to fan out to', () => {
    const summary = describePublication(publication({ publishedAt: now - MINUTE, nodes: 2 }), now);

    expect(summary.detail).toContain('sin contactos íntimos');
  });

  it('separates a network failure from a certificate one', () => {
    const network = describePublication(publication({ error: { kind: 'red', message: 'socket cerrado', at: now - MINUTE } }), now);
    const certificate = describePublication(publication({ error: { kind: 'certificado', message: 'no válido', at: now - MINUTE } }), now);

    expect(network.tone).toBe('danger');
    expect(network.title).toBe('Fallo de red');
    expect(certificate.tone).toBe('danger');
    expect(certificate.title).toBe('Certificado rechazado');
  });

  it('keeps an identity problem as a warning instead of a failure', () => {
    const summary = describePublication(publication({ error: { kind: 'identidad', message: 'sin cuenta', at: now } }), now);

    expect(summary.tone).toBe('warn');
    expect(summary.title).toBe('Sin identidad de cuenta');
  });

  it('shows when the error happened', () => {
    const summary = describePublication(publication({ error: { kind: 'red', message: 'timeout', at: now - 3 * MINUTE } }), now);

    expect(summary.detail).toBe('timeout (hace 3 minutos)');
  });
});

describe('describeLocationAddress', () => {
  it('drops empty addresses', () => {
    expect(describeLocationAddress('')).toBeNull();
    expect(describeLocationAddress('   ')).toBeNull();
    expect(describeLocationAddress(undefined)).toBeNull();
  });

  it('keeps the address', () => {
    expect(describeLocationAddress(' 200::1 ')).toBe('200::1');
  });
});
