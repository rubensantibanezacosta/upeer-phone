export type TransferState = 'pending' | 'active' | 'completed' | 'failed' | 'cancelled';

export type TransferPhase =
  | 'proposed'
  | 'initializing'
  | 'ready'
  | 'transferring'
  | 'verifying'
  | 'completing'
  | 'done'
  | 'replicating'
  | 'vaulted';

export type TransferDirection = 'sending' | 'receiving';

export const TRANSFER_CONFIG = {
  maxChunkSize: 1024 * 64,
  maxFileSize: 10 * 1024 * 1024 * 1024,
  transferTimeout: 300_000,
  maxRetries: 5,
  cleanupInterval: 60_000,
  initialWindowSize: 16,
  maxWindowSize: 128,
  maxConcurrentTransfers: 3,
} as const;

export type TransferPlan = {
  chunkSize: number;
  totalChunks: number;
};

export type ChunkRange = {
  start: number;
  end: number;
  length: number;
};

const ALLOWED: Record<TransferPhase, TransferPhase[]> = {
  proposed: ['initializing'],
  initializing: ['ready'],
  ready: ['transferring'],
  transferring: ['verifying'],
  verifying: ['completing'],
  completing: ['done'],
  done: ['replicating'],
  replicating: ['vaulted', 'done'],
  vaulted: [],
};

export function planTransfer(fileSize: number, chunkSize: number = TRANSFER_CONFIG.maxChunkSize): TransferPlan {
  if (!Number.isFinite(fileSize) || fileSize < 0) throw new Error('El tamaño del archivo no es válido.');
  if (fileSize > TRANSFER_CONFIG.maxFileSize) throw new Error('El archivo supera el tamaño máximo admitido.');
  if (chunkSize <= 0 || chunkSize > TRANSFER_CONFIG.maxChunkSize) throw new Error('El tamaño de trozo no es válido.');
  return { chunkSize, totalChunks: Math.max(1, Math.ceil(fileSize / chunkSize)) };
}

export function isValidChunkIndex(index: number, totalChunks: number): boolean {
  return Number.isInteger(index) && index >= 0 && index < totalChunks;
}

export function chunkRange(index: number, fileSize: number, chunkSize: number): ChunkRange | null {
  if (!isValidChunkIndex(index, planTransfer(fileSize, chunkSize).totalChunks)) return null;
  const start = index * chunkSize;
  const end = Math.min(start + chunkSize, fileSize);
  return { start, end, length: end - start };
}

export function hexOf(bytes: ArrayLike<number>): string {
  let out = '';
  for (let index = 0; index < bytes.length; index += 1) out += bytes[index].toString(16).padStart(2, '0');
  return out;
}

export function sameDigest(expected: string, computed: string): boolean {
  const left = expected.replace(/\s+/g, '').toLowerCase();
  const right = computed.replace(/\s+/g, '').toLowerCase();
  if (left.length !== right.length || left.length === 0) return false;
  let diff = 0;
  for (let index = 0; index < left.length; index += 1) diff |= left.charCodeAt(index) ^ right.charCodeAt(index);
  return diff === 0;
}

export function advancePhase(current: TransferPhase, target: TransferPhase): TransferPhase {
  return ALLOWED[current].includes(target) ? target : current;
}

export function canAcceptChunk(range: ChunkRange, fileSize: number, chunkSize: number): boolean {
  return range.length > 0 && range.length <= chunkSize && range.end <= fileSize;
}

export function transferProgress(receivedBytes: number, fileSize: number): number {
  if (fileSize <= 0) return 0;
  return Math.min(100, Math.max(0, Math.round((receivedBytes / fileSize) * 100)));
}

export function shouldRetry(attempts: number, maxRetries: number = TRANSFER_CONFIG.maxRetries): boolean {
  return attempts < maxRetries;
}

export function isTransferStale(lastActivity: number, now: number, timeout: number = TRANSFER_CONFIG.transferTimeout): boolean {
  return now - lastActivity > timeout;
}

export function growWindow(current: number, acknowledgedChunks: number): number {
  let window = current;
  for (let index = 0; index < acknowledgedChunks; index += 1) {
    window = Math.min(TRANSFER_CONFIG.maxWindowSize, window * 2);
  }
  return window;
}

export type FileMessageDraft = {
  fileId: string;
  fileName: string;
  fileSize: number;
  mimeType: string;
  savedPath?: string;
  thumbnail?: string;
  caption?: string;
  isVoiceNote?: boolean;
};

export function buildFileMessagePayload(draft: FileMessageDraft): string {
  return JSON.stringify({
    type: 'file',
    transferId: draft.fileId,
    fileId: draft.fileId,
    fileName: draft.fileName,
    fileSize: draft.fileSize,
    mimeType: draft.mimeType,
    savedPath: draft.savedPath,
    thumbnail: draft.thumbnail,
    caption: draft.caption,
    ...(draft.isVoiceNote ? { isVoiceNote: true } : {}),
  });
}
