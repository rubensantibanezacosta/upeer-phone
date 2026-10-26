export type AttachmentKind = 'image' | 'video' | 'voice' | 'document';

export type AttachmentInfo = {
  kind: AttachmentKind;
  fileName: string;
  fileSize: number;
  mimeType: string;
  transferId: string;
  caption?: string;
  thumbnail?: string;
  localPath?: string;
};

const MAX_TEXT_LENGTH = 400;
const MAX_PATH_LENGTH = 1024;
const LEGACY_PREFIX = 'FILE_TRANSFER|';

function kindOf(mimeType: string, fileName: string, isVoiceNote: boolean, declaredImage: boolean): AttachmentKind {
  if (isVoiceNote) return 'voice';
  const mime = mimeType.toLowerCase();
  if (mime.startsWith('image/')) return 'image';
  if (mime.startsWith('video/') || mime === 'video/x-matroska') return 'video';
  const extension = fileExtension(fileName).toLowerCase();
  if (['jpg', 'jpeg', 'png', 'gif', 'webp', 'heic', 'bmp', 'avif'].includes(extension)) return 'image';
  if (['mp4', 'mov', 'avi', 'webm', 'mkv'].includes(extension)) return 'video';
  return declaredImage ? 'image' : 'document';
}

export function fileExtension(fileName: string): string {
  const parts = fileName.split('.');
  if (parts.length < 2) return '';
  const extension = parts[parts.length - 1].trim();
  return extension.length > 0 && extension.length <= 8 ? extension.toUpperCase() : '';
}

function text(value: unknown): string {
  return typeof value === 'string' ? value.slice(0, MAX_TEXT_LENGTH) : '';
}

function localPathOf(value: unknown): string | undefined {
  if (typeof value !== 'string') return undefined;
  const path = value.trim();
  if (path.length === 0 || path.length > MAX_PATH_LENGTH) return undefined;
  if (!path.startsWith('/') && !path.startsWith('file://')) return undefined;
  if (path.includes('\u0000')) return undefined;
  return path;
}

function buildAttachment(payload: Record<string, unknown>, declaredImage: boolean): AttachmentInfo {
  const fileName = text(payload.fileName);
  const mimeType = text(payload.mimeType);
  const thumbnail = text(payload.thumbnail);
  const caption = text(payload.caption).trim();
  return {
    kind: kindOf(mimeType, fileName, payload.isVoiceNote === true, declaredImage),
    fileName: fileName || 'Archivo',
    fileSize: typeof payload.fileSize === 'number' && Number.isFinite(payload.fileSize) ? payload.fileSize : 0,
    mimeType,
    transferId: text(payload.transferId) || text(payload.fileId),
    caption: caption || undefined,
    thumbnail: thumbnail.startsWith('data:image/') ? thumbnail : undefined,
    localPath: localPathOf(payload.savedPath),
  };
}

function parseLegacy(content: string): AttachmentInfo | null {
  const parts = content.split('|');
  if (parts.length < 5) return null;
  const size = Number(parts[3]);
  return buildAttachment({
    fileName: parts[2],
    fileSize: Number.isFinite(size) ? size : 0,
    mimeType: parts[4],
    transferId: parts[1],
  }, false);
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
  return buildAttachment(payload, payload.type === 'image');
}

export function parseAttachmentMessage(content: string): AttachmentInfo | null {
  return parseAttachment(content) ?? (content.startsWith(LEGACY_PREFIX) ? parseLegacy(content) : null);
}

export function formatFileSize(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes <= 0) return '';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} kB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(1)} GB`;
}

export function attachmentLabel(info: AttachmentInfo): string {
  return info.kind === 'voice' ? 'Nota de voz' : info.fileName;
}

export function attachmentUri(info: AttachmentInfo): string | null {
  const path = info.localPath;
  if (!path) return null;
  if (path.startsWith('file://')) return path;
  return `file://${path.split('/').map((segment) => encodeURIComponent(segment)).join('/')}`;
}

export function canOpenAttachment(info: AttachmentInfo): boolean {
  return attachmentUri(info) !== null;
}

export function attachmentDetail(info: AttachmentInfo): string {
  return [fileExtension(info.fileName), formatFileSize(info.fileSize)].filter(Boolean).join(' · ');
}

export function describeMessageContent(content: string): string {
  const info = parseAttachmentMessage(content);
  return info ? info.caption ?? attachmentLabel(info) : content;
}
