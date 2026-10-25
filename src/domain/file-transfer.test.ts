import { describe, expect, it } from 'vitest';
import {
  TRANSFER_CONFIG,
  advancePhase,
  buildFileMessagePayload,
  canAcceptChunk,
  chunkRange,
  growWindow,
  hexOf,
  isTransferStale,
  isValidChunkIndex,
  planTransfer,
  sameDigest,
  shouldRetry,
  transferProgress,
} from './file-transfer';

describe('file-transfer planning', () => {
  it('divide el archivo en trozos del tamaño máximo', () => {
    expect(planTransfer(TRANSFER_CONFIG.maxChunkSize * 3)).toEqual({
      chunkSize: TRANSFER_CONFIG.maxChunkSize,
      totalChunks: 3,
    });
  });

  it('redondea hacia arriba cuando el último trozo es parcial', () => {
    expect(planTransfer(10, 4).totalChunks).toBe(3);
  });

  it('un archivo vacío sigue teniendo un trozo', () => {
    expect(planTransfer(0)).toEqual({ chunkSize: TRANSFER_CONFIG.maxChunkSize, totalChunks: 1 });
  });

  it('rechaza tamaños negativos, no finitos y superiores al máximo', () => {
    expect(() => planTransfer(-1)).toThrow();
    expect(() => planTransfer(Number.POSITIVE_INFINITY)).toThrow();
    expect(() => planTransfer(TRANSFER_CONFIG.maxFileSize + 1)).toThrow();
  });

  it('rechaza trozos fuera de rango', () => {
    expect(() => planTransfer(10, 0)).toThrow();
    expect(() => planTransfer(10, TRANSFER_CONFIG.maxChunkSize + 1)).toThrow();
  });
});

describe('file-transfer chunks', () => {
  it('calcula el rango de cada trozo', () => {
    expect(chunkRange(0, 10, 4)).toEqual({ start: 0, end: 4, length: 4 });
    expect(chunkRange(2, 10, 4)).toEqual({ start: 8, end: 10, length: 2 });
  });

  it('devuelve null fuera de rango', () => {
    expect(chunkRange(3, 10, 4)).toBeNull();
    expect(chunkRange(-1, 10, 4)).toBeNull();
    expect(chunkRange(1.5, 10, 4)).toBeNull();
  });

  it('valida índices enteros', () => {
    expect(isValidChunkIndex(0, 3)).toBe(true);
    expect(isValidChunkIndex(3, 3)).toBe(false);
    expect(isValidChunkIndex(Number.NaN, 3)).toBe(false);
  });

  it('acepta solo trozos con longitud útil dentro del archivo', () => {
    expect(canAcceptChunk({ start: 0, end: 4, length: 4 }, 10, 4)).toBe(true);
    expect(canAcceptChunk({ start: 0, end: 5, length: 5 }, 10, 4)).toBe(false);
    expect(canAcceptChunk({ start: 8, end: 10, length: 2 }, 10, 4)).toBe(true);
    expect(canAcceptChunk({ start: 0, end: 0, length: 0 }, 10, 4)).toBe(false);
    expect(canAcceptChunk({ start: 8, end: 12, length: 4 }, 10, 4)).toBe(false);
  });
});

describe('file-transfer digests', () => {
  it('serializa bytes a hexadecimal con dos dígitos', () => {
    expect(hexOf([0, 1, 15, 16, 255])).toBe('00010f10ff');
    expect(hexOf([])).toBe('');
  });

  it('compara digests sin distinguir mayúsculas ni espacios', () => {
    expect(sameDigest(' AB CD ', 'abcd')).toBe(true);
    expect(sameDigest('abcd', 'abce')).toBe(false);
    expect(sameDigest('abcd', 'abcde')).toBe(false);
    expect(sameDigest('', '')).toBe(false);
  });
});

describe('file-transfer phases', () => {
  it('avanza por la cadena completa', () => {
    expect(advancePhase('proposed', 'initializing')).toBe('initializing');
    expect(advancePhase('initializing', 'ready')).toBe('ready');
    expect(advancePhase('ready', 'transferring')).toBe('transferring');
    expect(advancePhase('transferring', 'verifying')).toBe('verifying');
    expect(advancePhase('verifying', 'completing')).toBe('completing');
    expect(advancePhase('completing', 'done')).toBe('done');
    expect(advancePhase('done', 'replicating')).toBe('replicating');
    expect(advancePhase('replicating', 'vaulted')).toBe('vaulted');
  });

  it('permite volver de replicating a done si la réplica no aplica', () => {
    expect(advancePhase('replicating', 'done')).toBe('done');
  });

  it('bloquea saltos y retrocesos', () => {
    expect(advancePhase('proposed', 'done')).toBe('proposed');
    expect(advancePhase('done', 'transferring')).toBe('done');
    expect(advancePhase('vaulted', 'done')).toBe('vaulted');
  });
});

describe('file-transfer progress', () => {
  it('calcula el porcentaje entero', () => {
    expect(transferProgress(0, 100)).toBe(0);
    expect(transferProgress(50, 100)).toBe(50);
    expect(transferProgress(100, 100)).toBe(100);
  });

  it('acota el porcentaje entre 0 y 100', () => {
    expect(transferProgress(150, 100)).toBe(100);
    expect(transferProgress(-5, 100)).toBe(0);
    expect(transferProgress(10, 0)).toBe(0);
  });

  it('reintenta hasta el límite configurado', () => {
    expect(shouldRetry(0)).toBe(true);
    expect(shouldRetry(TRANSFER_CONFIG.maxRetries)).toBe(false);
    expect(shouldRetry(2, 3)).toBe(true);
    expect(shouldRetry(3, 3)).toBe(false);
  });

  it('detecta transferencias paradas', () => {
    expect(isTransferStale(1_000, 1_000)).toBe(false);
    expect(isTransferStale(1_000, 1_000 + TRANSFER_CONFIG.transferTimeout)).toBe(false);
    expect(isTransferStale(1_000, 1_001 + TRANSFER_CONFIG.transferTimeout)).toBe(true);
  });

  it('duplica la ventana sin superar el máximo', () => {
    expect(growWindow(TRANSFER_CONFIG.initialWindowSize, 0)).toBe(16);
    expect(growWindow(TRANSFER_CONFIG.initialWindowSize, 1)).toBe(32);
    expect(growWindow(16, 100)).toBe(TRANSFER_CONFIG.maxWindowSize);
  });
});

describe('buildFileMessagePayload', () => {
  it('usa el mismo sobre que el escritorio', () => {
    const payload = JSON.parse(
      buildFileMessagePayload({
        fileId: 'abc',
        fileName: 'foto.jpg',
        fileSize: 12,
        mimeType: 'image/jpeg',
        savedPath: 'file:///tmp/foto.jpg',
      }),
    );

    expect(payload).toEqual({
      type: 'file',
      transferId: 'abc',
      fileId: 'abc',
      fileName: 'foto.jpg',
      fileSize: 12,
      mimeType: 'image/jpeg',
      savedPath: 'file:///tmp/foto.jpg',
    });
  });

  it('solo marca isVoiceNote cuando aplica', () => {
    const base = { fileId: 'a', fileName: 'v.m4a', fileSize: 1, mimeType: 'audio/mp4' };
    expect(JSON.parse(buildFileMessagePayload(base)).isVoiceNote).toBeUndefined();
    expect(JSON.parse(buildFileMessagePayload({ ...base, isVoiceNote: true })).isVoiceNote).toBe(true);
  });
});
