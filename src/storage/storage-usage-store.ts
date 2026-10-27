import { Directory, Paths } from 'expo-file-system';

import { EMPTY_STORAGE_USAGE, summarizeStorage, type StorageUsage } from '@/domain/storage-usage';

export type StorageUsageSnapshot = {
  error: string;
  loaded: boolean;
  usage: StorageUsage;
};

const DATABASE_DIRECTORY = new Directory(Paths.document, 'SQLite');
const DATA_DIRECTORIES = [new Directory(Paths.document, 'attachments'), new Directory(Paths.document, 'profile')];

const INITIAL_SNAPSHOT: StorageUsageSnapshot = { error: '', loaded: false, usage: EMPTY_STORAGE_USAGE };
const listeners = new Set<() => void>();
let snapshot = INITIAL_SNAPSHOT;

type DirectoryMeasure = { bytes: number; files: number };

function walk(directory: Directory): DirectoryMeasure {
  if (!directory.exists) return { bytes: 0, files: 0 };
  return directory.list().reduce<DirectoryMeasure>((total, entry) => {
    if (entry instanceof Directory) {
      const nested = walk(entry);
      return { bytes: total.bytes + nested.bytes, files: total.files + nested.files };
    }
    const size = entry.size;
    const bytes = typeof size === 'number' && Number.isFinite(size) && size > 0 ? size : 0;
    return { bytes: total.bytes + bytes, files: total.files + 1 };
  }, { bytes: 0, files: 0 });
}

function sizeOf(directory: Directory): number {
  if (!directory.exists) return 0;
  const size = directory.size;
  return typeof size === 'number' && Number.isFinite(size) && size > 0 ? size : 0;
}

function sameUsage(a: StorageUsage, b: StorageUsage): boolean {
  return a.attachmentCount === b.attachmentCount
    && a.attachmentsBytes === b.attachmentsBytes
    && a.databaseBytes === b.databaseBytes
    && a.ownBytes === b.ownBytes;
}

function publish(next: StorageUsageSnapshot): void {
  if (next.error === snapshot.error && next.loaded === snapshot.loaded && sameUsage(next.usage, snapshot.usage)) return;
  snapshot = next;
  for (const listener of listeners) listener();
}

export function getStorageUsageSnapshot(): StorageUsageSnapshot {
  return snapshot;
}

export function subscribeStorageUsage(listener: () => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

export function refreshStorageUsage(): void {
  try {
    const data = DATA_DIRECTORIES.reduce<DirectoryMeasure>((total, directory) => {
      const measured = walk(directory);
      return { bytes: total.bytes + measured.bytes, files: total.files + measured.files };
    }, { bytes: 0, files: 0 });
    const usage = summarizeStorage({
      attachmentCount: data.files,
      attachmentsBytes: data.bytes,
      databaseBytes: sizeOf(DATABASE_DIRECTORY),
    });
    publish({ error: '', loaded: true, usage });
  } catch {
    publish({ error: 'No se pudo leer el uso de disco de este dispositivo', loaded: true, usage: snapshot.usage });
  }
}
