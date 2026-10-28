import type { DhtLocationPublication } from '../../modules/upeer-transport/src/UPeerTransport.types';

const MINUTE = 60 * 1000;
const HOUR = 60 * MINUTE;

export type LocationTone = 'ok' | 'warn' | 'danger' | 'idle';

export type LocationSummary = {
  detail: string;
  title: string;
  tone: LocationTone;
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

export function describePublication(publication: DhtLocationPublication | null | undefined, now: number): LocationSummary {
  if (!publication) {
    return { detail: 'El servicio todavía no ha publicado tu ubicación.', title: 'Sin datos de publicación', tone: 'idle' };
  }

  const published = describeAge(publication.publishedAt, now);
  const error = publication.error;

  if (error) {
    const when = describeAge(error.at, now);
    const suffix = when ? ` (${when})` : '';
    const prefix = error.kind === 'certificado'
      ? 'Certificado rechazado'
      : error.kind === 'identidad'
        ? 'Sin identidad de cuenta'
        : 'Fallo de red';
    return { detail: `${error.message}${suffix}`, title: prefix, tone: error.kind === 'identidad' ? 'warn' : 'danger' };
  }

  if (!published) {
    return { detail: 'Todavía no se ha publicado tu bloque en la DHT.', title: 'Sin publicar', tone: 'warn' };
  }

  const fanout = publication.fanout > 0
    ? `Repartido a ${publication.fanout} ${publication.fanout === 1 ? 'contacto' : 'contactos'}`
    : 'Todavía sin contactos íntimos a los que repartir';
  const nodes = publication.nodes > 0
    ? `${publication.nodes} ${publication.nodes === 1 ? 'nodo' : 'nodos'}`
    : 'ningún nodo';

  return { detail: `Publicado ${published} en ${nodes}. ${fanout}.`, title: 'Ubicación publicada', tone: 'ok' };
}

export function describeLocationAddress(address?: string | null): string | null {
  if (!address) return null;
  const trimmed = address.trim();
  return trimmed.length > 0 ? trimmed : null;
}
