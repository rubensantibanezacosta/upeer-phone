import { describe, expect, it } from 'vitest';

import { describeSharedFiles, EMPTY_STORAGE_USAGE, formatStorageSize, summarizeStorage } from '@/domain/storage-usage';

describe('formatStorageSize', () => {
  it('redondea los ceros y los negativos a cero', () => {
    expect(formatStorageSize(0)).toBe('0 B');
    expect(formatStorageSize(-12)).toBe('0 B');
    expect(formatStorageSize(Number.NaN)).toBe('0 B');
  });

  it('usa bytes hasta el kilobyte', () => {
    expect(formatStorageSize(1)).toBe('1 B');
    expect(formatStorageSize(1023)).toBe('1023 B');
  });

  it('sube de unidad con un decimal', () => {
    expect(formatStorageSize(1024)).toBe('1.0 KB');
    expect(formatStorageSize(1536)).toBe('1.5 KB');
    expect(formatStorageSize(5 * 1024 * 1024)).toBe('5.0 MB');
    expect(formatStorageSize(2 * 1024 ** 3)).toBe('2.0 GB');
  });

  it('quita los decimales en valores grandes', () => {
    expect(formatStorageSize(512 * 1024)).toBe('512 KB');
    expect(formatStorageSize(700 * 1024 * 1024)).toBe('700 MB');
  });
});

describe('describeSharedFiles', () => {
  it('concuerda en singular y plural', () => {
    expect(describeSharedFiles(0)).toBe('Sin archivos en la carpeta de datos');
    expect(describeSharedFiles(1)).toBe('1 archivo en la carpeta de datos');
    expect(describeSharedFiles(4)).toBe('4 archivos en la carpeta de datos');
  });
});

describe('summarizeStorage', () => {
  it('suma base de datos y archivos compartidos', () => {
    const usage = summarizeStorage({ attachmentCount: 2, attachmentsBytes: 2048, databaseBytes: 4096 });
    expect(usage.ownBytes).toBe(6144);
    expect(usage.attachmentCount).toBe(2);
  });

  it('sanea los valores imposibles', () => {
    const usage = summarizeStorage({ attachmentCount: Number.NaN, attachmentsBytes: -1, databaseBytes: 10 });
    expect(usage).toEqual({ ...EMPTY_STORAGE_USAGE, databaseBytes: 10, ownBytes: 10 });
  });

  it('trunca el contador de archivos', () => {
    expect(summarizeStorage({ attachmentCount: 3.9, attachmentsBytes: 0, databaseBytes: 0 }).attachmentCount).toBe(3);
  });
});
