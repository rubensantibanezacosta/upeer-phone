export type AttachmentKind = 'image' | 'video' | 'audio' | 'voice' | 'document' | 'other';

export type AttachmentInfo = {
  kind: AttachmentKind;
  fileName: string;
  fileSize: number;
  mimeType: string;
  transferId: string;
  caption?: string;
};

const MAX_LABEL_LENGTH = 120;

const KIND_LABELS: Record<AttachmentKind, string> = {
  image: 'Foto',
  video: 'Vídeo',
  audio: 'Audio',
  voice: 'Nota de voz',
  document: 'Documento',
  other: 'Archivo',
};

function kindOf(mimeType: string, fileName: string, isVoiceNote: boolean, declaredImage: boolean): AttachmentKind {
  if (isVoiceNote) return 'voice';
  const mime = mimeType.toLowerCase();
  if (mime.startsWith('image/')) return 'image';
  if (mime.startsWith('video/')) return 'video';
  if (mime.startsWith('audio/')) return 'audio';  const extension = fileName.toLowerCase().split('.').pop() ?? '';
  if (['jpg', 'jpeg', 'png', 'gif', 'webp', 'heic'].includes(extension)) return 'image';
  if (['mp4', 'mov', 'avi', 'webm', 'mkv'].includes(extension)) return 'video';
  if (['mp3', 'wav', 'ogg', 'm4a', 'aac', 'opus'].includes(extension)) return 'audio';
  if (['pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'txt', 'csv', 'zip'].includes(extension)) return 'document';
  return declaredImage ? 'image' : 'other';
}

export function parseAttachment(content: string): AttachmentInfo | null {
  const trimmed = content.trimStart();
  if (!trimmed.startsWith('{')) return null;
  let parsed: unknown;
  try {
    parsed = JSON.parse(trimmed);
  } catch {
    return null;
  }
  if (!parsed || typeof parsed !== 'object') return null;
  const payload = parsed as Record<string, unknown>;
  if (payload.type !== 'file' && payload.type !== 'image') return null;
  const fileName = typeof payload.fileName === 'string' ? payload.fileName.slice(0, MAX_LABEL_LENGTH) : '';
  const mimeType = typeof payload.mimeType === 'string' ? payload.mimeType.slice(0, MAX_LABEL_LENGTH) : '';
  const transferId = typeof payload.transferId === 'string' ? payload.transferId : typeof payload.fileId === 'string' ? payload.fileId : '';
  const fileSize = typeof payload.fileSize === 'number' && Number.isFinite(payload.fileSize) ? payload.fileSize : 0;
  const caption = typeof payload.caption === 'string' && payload.caption.trim()
    ? payload.caption.trim().slice(0, MAX_LABEL_LENGTH)
    : undefined;
  return {
    kind: kindOf(mimeType, fileName, payload.isVoiceNote === true, payload.type === 'image'),
    fileName: fileName || 'Archivo',
    fileSize,
    mimeType,
    transferId,
    caption,
  };
}

export function formatFileSize(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes <= 0) return '';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} kB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(1)} GB`;
}

export function attachmentLabel(info: AttachmentInfo): string {
  const base = KIND_LABELS[info.kind];
  if (info.kind === 'image' || info.kind === 'video' || info.kind === 'voice') return base;
  return info.fileName ? `${base}: ${info.fileName}` : base;
}

export function attachmentSummary(content: string): string | null {
  const info = parseAttachment(content);
  if (!info) return null;
  return info.caption ?? attachmentLabel(info);
}

export function describeMessageContent(content: string): string {
  return attachmentSummary(content) ?? content;
}
