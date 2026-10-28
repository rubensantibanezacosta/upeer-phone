import type { VaultUsage } from '../../modules/upeer-transport/src/UPeerTransportModule';

export type { VaultUsage };

export const EMPTY_VAULT_USAGE: VaultUsage = { bytes: 0, entries: 0, expiringSoon: 0, maxEntries: 0 };

function count(value: number): number {
  if (!Number.isFinite(value)) return 0;
  return Math.max(0, Math.trunc(value));
}

function group(value: number): string {
  return String(value).replace(/\B(?=(\d{3})+(?!\d))/g, '.');
}

export function describeVaultEntries(usage: VaultUsage): string {
  const entries = count(usage.entries);
  const limit = count(usage.maxEntries);
  if (limit <= 0) return entries === 1 ? '1 mensaje' : `${group(entries)} mensajes`;
  return `${group(entries)} de ${group(limit)} mensajes`;
}

export function vaultFillPercent(usage: VaultUsage): number {
  const limit = count(usage.maxEntries);
  if (limit <= 0) return 0;
  return Math.min(Math.round((count(usage.entries) / limit) * 100), 100);
}

export function describeVaultHint(usage: VaultUsage): string {
  if (count(usage.entries) <= 0) return 'No custodias ningún mensaje pendiente.';
  const base = 'Cada mensaje se borra al entregarse a su destinatario o al caducar.';
  const soon = count(usage.expiringSoon);
  if (soon <= 0) return base;
  return `${base} ${soon === 1 ? 'Uno caduca' : `${group(soon)} caducan`} en las próximas 24 horas.`;
}
