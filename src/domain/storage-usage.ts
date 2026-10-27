export type StorageBreakdown = {
  attachmentCount: number;
  attachmentsBytes: number;
  avatarsBytes: number;
  cacheBytes: number;
  databaseBytes: number;
};

export type StorageUsage = StorageBreakdown & {
  ownBytes: number;
};

export const EMPTY_STORAGE_USAGE: StorageUsage = {
  attachmentCount: 0,
  attachmentsBytes: 0,
  avatarsBytes: 0,
  cacheBytes: 0,
  databaseBytes: 0,
  ownBytes: 0,
};

const UNITS = ['B', 'kB', 'MB', 'GB', 'TB'];

function toBytes(value: number): number {
  if (!Number.isFinite(value) || value <= 0) return 0;
  return Math.round(value);
}

export function formatStorageSize(value: number): string {
  const total = toBytes(value);
  if (total < 1024) return `${total} B`;
  const exponent = Math.min(Math.floor(Math.log(total) / Math.log(1024)), UNITS.length - 1);
  const scaled = total / 1024 ** exponent;
  const decimals = scaled >= 100 ? 0 : 1;
  return `${scaled.toFixed(decimals)} ${UNITS[exponent]}`;
}

export function describeAttachmentCount(count: number): string {
  if (count <= 0) return 'Sin archivos recibidos';
  if (count === 1) return '1 archivo recibido';
  return `${count} archivos recibidos`;
}

export function summarizeStorage(breakdown: StorageBreakdown): StorageUsage {
  const databaseBytes = toBytes(breakdown.databaseBytes);
  const attachmentsBytes = toBytes(breakdown.attachmentsBytes);
  const avatarsBytes = toBytes(breakdown.avatarsBytes);
  const cacheBytes = toBytes(breakdown.cacheBytes);
  const attachmentCount = Number.isFinite(breakdown.attachmentCount) && breakdown.attachmentCount > 0 ? Math.floor(breakdown.attachmentCount) : 0;
  return {
    attachmentCount,
    attachmentsBytes,
    avatarsBytes,
    cacheBytes,
    databaseBytes,
    ownBytes: databaseBytes + attachmentsBytes + avatarsBytes,
  };
}
