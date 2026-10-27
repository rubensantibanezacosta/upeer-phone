export type StorageBreakdown = {
  attachmentCount: number;
  attachmentsBytes: number;
  databaseBytes: number;
};

export type StorageUsage = StorageBreakdown & {
  ownBytes: number;
};

export const EMPTY_STORAGE_USAGE: StorageUsage = {
  attachmentCount: 0,
  attachmentsBytes: 0,
  databaseBytes: 0,
  ownBytes: 0,
};

const UNITS = ['B', 'KB', 'MB', 'GB', 'TB'];

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

export function describeSharedFiles(count: number): string {
  if (count <= 0) return 'Sin archivos en la carpeta de datos';
  if (count === 1) return '1 archivo en la carpeta de datos';
  return `${count} archivos en la carpeta de datos`;
}

export function summarizeStorage(breakdown: StorageBreakdown): StorageUsage {
  const databaseBytes = toBytes(breakdown.databaseBytes);
  const attachmentsBytes = toBytes(breakdown.attachmentsBytes);
  const attachmentCount = Number.isFinite(breakdown.attachmentCount) && breakdown.attachmentCount > 0 ? Math.floor(breakdown.attachmentCount) : 0;
  return {
    attachmentCount,
    attachmentsBytes,
    databaseBytes,
    ownBytes: databaseBytes + attachmentsBytes,
  };
}
