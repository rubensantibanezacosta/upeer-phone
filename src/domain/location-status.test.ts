import { describe, expect, it } from 'vitest';

import { describeAge, describeLocationAddress, describeLocationState } from './location-status';
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

describe('describeLocationState', () => {
  const now = 100 * HOUR;

  it('marks a node with no publication as pending', () => {
    const state = describeLocationState(null, now);

    expect(state.published).toBe(false);
    expect(state.rows).toEqual([
      { label: 'Última publicación', value: 'Todavía no se ha publicado.' },
      { label: 'Copias en la red', value: 'Ningún nodo todavía.' },
      { label: 'Contactos avisados', value: 'Sin contactos íntimos todavía.' },
    ]);
    expect(state.errors).toEqual([]);
  });

  it('describes a successful publication without claiming a phone location', () => {
    const state = describeLocationState(publication({ publishedAt: now - 2 * MINUTE, dhtSeq: 7, nodes: 4, fanout: 3 }), now);

    expect(state.published).toBe(true);
    expect(state.rows[0].value).toBe('Publicada hace 2 minutos · secuencia 7.');
    expect(state.rows[1].value).toBe('En 4 nodos.');
    expect(state.rows[2].value).toBe('A 3 contactos.');
  });

  it('uses the singular where it applies', () => {
    const state = describeLocationState(publication({ publishedAt: now - MINUTE, nodes: 1, fanout: 1 }), now);

    expect(state.rows[1].value).toBe('En 1 nodo.');
    expect(state.rows[2].value).toBe('A 1 contacto.');
  });

  it('labels each error by origin and keeps when it happened', () => {
    const network = describeLocationState(publication({ error: { kind: 'red', message: 'timeout', at: now - 3 * MINUTE } }), now);
    const certificate = describeLocationState(publication({ error: { kind: 'certificado', message: 'no válido', at: now } }), now);
    const identity = describeLocationState(publication({ error: { kind: 'identidad', message: 'sin cuenta', at: now } }), now);

    expect(network.errors).toEqual([{ label: 'Red', detail: 'timeout (hace 3 minutos)' }]);
    expect(certificate.errors[0].label).toBe('Certificado');
    expect(identity.errors[0].label).toBe('Identidad');
  });

  it('falls back to a generic label for an unknown origin', () => {
    const state = describeLocationState(publication({ error: { kind: 'otra', message: 'algo', at: 0 } }), now);

    expect(state.errors[0].label).toBe('Aviso');
    expect(state.errors[0].detail).toBe('algo');
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
