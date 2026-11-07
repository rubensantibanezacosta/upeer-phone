import { parseAttachment, type AttachmentInfo } from './attachment';

export type SharedMediaItem = {
  attachment: AttachmentInfo;
  fileId: string;
  messageId: string;
  timestamp: number;
  fileName: string;
  isVideo: boolean;
  preview?: string;
};

export const SHARED_MEDIA_LIMIT = 5;
const MEDIA_SCAN_LIMIT = 40;

type MediaSource = {
  id: string;
  content: string;
  timestamp: number;
};

export function collectSharedMedia(messages: readonly MediaSource[], limit = MEDIA_SCAN_LIMIT): SharedMediaItem[] {
  const items: SharedMediaItem[] = [];
  const seen = new Set<string>();
  for (const message of messages) {
    if (seen.has(message.id)) continue;
    const attachment = parseAttachment(message.content);
    if (!attachment) continue;
    if (attachment.kind !== 'image' && attachment.kind !== 'video') continue;
    const key = attachment.transferId || message.id;
    if (seen.has(key)) continue;
    seen.add(key);
    items.push({
      attachment,
      fileId: attachment.transferId,
      messageId: message.id,
      timestamp: message.timestamp,
      fileName: attachment.fileName,
      isVideo: attachment.kind === 'video',
      preview: previewOf(attachment),
    });
    if (items.length >= limit) break;
  }
  return items;
}

function previewOf(attachment: ReturnType<typeof parseAttachment>): string | undefined {
  if (!attachment) return undefined;
  if (attachment.thumbnail) return attachment.thumbnail;
  if (attachment.kind === 'image' && attachment.localPath) return attachment.localPath;
  return undefined;
}

export function sharedMediaCount(messages: readonly MediaSource[]): number {
  return collectSharedMedia(messages, Number.MAX_SAFE_INTEGER).length;
}
