import type { DhtLocationPublication } from '../../modules/upeer-transport/src/UPeerTransport.types';

const MINUTE = 60 * 1000;
const HOUR = 60 * MINUTE;

export type LocationRow = { label: string; value: string };

export type LocationError = { label: string; detail: string };

export type LocationState = {
  rows: LocationRow[];
  errors: LocationError[];
  published: boolean;
};

export const LOCATION_ERROR_LABELS: Record<string, string> = {
  certificado: 'Certificado',
  identidad: 'Identidad',
  red: 'Red',
};

export function describeAge(timestamp: number, now: number): string | null {
  if (!Number.isFinite(timestamp) || timestamp <= 0) return null;
  const elapsed = now - timestamp;
  if (!Number.isFinite(elapsed) || elapsed < 0) return 'ahora mismo';
  if (elapsed < MINUTE) return 'hace menos de un minuto';
  if (elapsed < HOUR) {
    const minutes = Math.floor(elapsed / MINUTE);
    return minutes === 1 ? 'hace 1 minuto' : `hace ${minutes} minutos`;
  }
  if (elapsed < 24 * HOUR) {
    const hours = Math.floor(elapsed / HOUR);
    return hours === 1 ? 'hace 1 hora' : `hace ${hours} horas`;
  }
  const days = Math.floor(elapsed / (24 * HOUR));
  return days === 1 ? 'hace 1 día' : `hace ${days} días`;
}

function describeNodes(nodes: number): string {
  if (nodes <= 0) return 'Ningún nodo todavía.';
  return nodes === 1 ? 'En 1 nodo.' : `En ${nodes} nodos.`;
}

function describeFanout(fanout: number): string {
  if (fanout <= 0) return 'Sin contactos íntimos todavía.';
  return fanout === 1 ? 'A 1 contacto.' : `A ${fanout} contactos.`;
}

export function describeLocationState(publication: DhtLocationPublication | null | undefined, now: number): LocationState {
  const published = Boolean(publication && publication.publishedAt > 0);
  const publishedAt = publication ? describeAge(publication.publishedAt, now) : null;
  const sequence = publication && publication.dhtSeq > 0 ? ` · secuencia ${publication.dhtSeq}` : '';
  const rows: LocationRow[] = [
    {
      label: 'Última publicación',
      value: published && publishedAt ? `Publicada ${publishedAt}${sequence}.` : 'Todavía no se ha publicado.',
    },
    { label: 'Copias en la red', value: describeNodes(publication?.nodes ?? 0) },
    { label: 'Contactos avisados', value: describeFanout(publication?.fanout ?? 0) },
  ];

  const error = publication?.error;
  const errors: LocationError[] = error
    ? [{
      label: LOCATION_ERROR_LABELS[error.kind] ?? 'Aviso',
      detail: `${error.message}${describeAge(error.at, now) ? ` (${describeAge(error.at, now)})` : ''}`,
    }]
    : [];

  return { rows, errors, published };
}

export function describeLocationAddress(address?: string | null): string | null {
  if (!address) return null;
  const trimmed = address.trim();
  return trimmed.length > 0 ? trimmed : null;
}
