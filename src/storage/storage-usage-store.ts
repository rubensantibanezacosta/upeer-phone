import { Directory, Paths } from 'expo-file-system';

import { EMPTY_STORAGE_USAGE, summarizeStorage, type StorageUsage } from '@/domain/storage-usage';

export type StorageUsageSnapshot = {
  error: string;
  loaded: boolean;
  usage: StorageUsage;
};

const DATABASE_DIRECTORY = new Directory(Paths.document, 'SQLite');
const ATTACHMENTS_DIRECTORY = new Directory(Paths.document, 'attachments');
const AVATAR_DIRECTORY = new Directory(Paths.document, 'profile');
const MANAGED_CACHE_NAMES = ['image_cache', 'ImagePicker', 'http-cache'];

const INITIAL_SNAPSHOT: StorageUsageSnapshot = { error: '', loaded: false, usage: EMPTY_STORAGE_USAGE };
const listeners = new Set<() => void>();
let snapshot = INITIAL_SNAPSHOT;

function sizeOf(directory: Directory): number {
  if (!directory.exists) return 0;
  const size = directory.size;
  return typeof size === 'number' && Number.isFinite(size) && size > 0 ? size : 0;
}

function countFiles(directory: Directory): number {
  if (!directory.exists) return 0;
  return directory.list().reduce((total, entry) => total + (entry instanceof Directory ? countFiles(entry) : 1), 0);
}

function managedCacheDirectory(name: string): Directory {
  return new Directory(Paths.cache, name);
}

function cacheBytes(): number {
  const rootFiles = Paths.cache.list().reduce((total, entry) => {
    if (entry instanceof Directory) return total;
    const size = entry.size;
    return typeof size === 'number' && Number.isFinite(size) ? total + size : total;
  }, 0);
  return MANAGED_CACHE_NAMES.reduce((total, name) => total + sizeOf(managedCacheDirectory(name)), rootFiles);
}

function sameUsage(a: StorageUsage, b: StorageUsage): boolean {
  return a.attachmentCount === b.attachmentCount
    && a.attachmentsBytes === b.attachmentsBytes
    && a.avatarsBytes === b.avatarsBytes
    && a.cacheBytes === b.cacheBytes
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
    const usage = summarizeStorage({
      attachmentCount: countFiles(ATTACHMENTS_DIRECTORY),
      attachmentsBytes: sizeOf(ATTACHMENTS_DIRECTORY),
      avatarsBytes: sizeOf(AVATAR_DIRECTORY),
      cacheBytes: cacheBytes(),
      databaseBytes: sizeOf(DATABASE_DIRECTORY),
    });
    publish({ error: '', loaded: true, usage });
  } catch {
    publish({ error: 'No se pudo leer el uso de disco de este dispositivo', loaded: true, usage: snapshot.usage });
  }
}

export function clearManagedCache(): void {
  for (const name of MANAGED_CACHE_NAMES) {
    const directory = managedCacheDirectory(name);
    if (directory.exists) directory.delete();
  }
  if (!Paths.cache.exists) return;
  for (const entry of Paths.cache.list()) {
    if (entry instanceof Directory) continue;
    entry.delete();
  }
}
