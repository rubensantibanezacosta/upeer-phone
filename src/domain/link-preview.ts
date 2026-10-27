export type LinkPreview = {
  url: string;
  title?: string;
  description?: string;
  imageBase64?: string;
  domain?: string;
};

export type LinkPreviewMessage = {
  preview: LinkPreview;
  text: string;
};

export const MAX_TEXT_LENGTH = 4000;
export const MAX_SERIALIZED_PREVIEW_CHARS = 40_000;
const MAX_TITLE_LENGTH = 200;
const MAX_DESCRIPTION_LENGTH = 400;
const MAX_IMAGE_LENGTH = 256 * 1024;
const HTTP_URL = /^https?:\/\//i;
const FIRST_URL = /(https?:\/\/[^\s<>"']+)/i;

function clean(value: unknown, limit: number): string | undefined {
  if (typeof value !== 'string') return undefined;
  const trimmed = value.trim();
  return trimmed ? trimmed.slice(0, limit) : undefined;
}

function dataImage(value: unknown): string | undefined {
  if (typeof value !== 'string') return undefined;
  if (!value.startsWith('data:image/') || value.length > MAX_IMAGE_LENGTH) return undefined;
  return value;
}

export function urlHostname(url: string): string {
  const match = HTTP_URL.exec(url);
  if (!match) return url;
  const rest = url.slice(match[0].length);
  const host = rest.split(/[/?#]/, 1)[0] ?? '';
  return host ? host.toLowerCase() : url;
}

export function sanitizeLinkPreview(value: unknown): LinkPreview | null {
  if (!value || typeof value !== 'object') return null;
  const link = value as Record<string, unknown>;
  const url = typeof link.url === 'string' ? link.url.trim() : '';
  if (!HTTP_URL.test(url)) return null;
  return {
    url,
    title: clean(link.title, MAX_TITLE_LENGTH),
    description: clean(link.description, MAX_DESCRIPTION_LENGTH),
    imageBase64: dataImage(link.imageBase64),
    domain: clean(link.domain, MAX_TITLE_LENGTH) ?? urlHostname(url),
  };
}

export function parseLinkPreviewMessage(content: string): LinkPreviewMessage | null {
  if (!content.startsWith('{') || !content.endsWith('}')) return null;
  let parsed: unknown;
  try {
    parsed = JSON.parse(content);
  } catch {
    return null;
  }
  if (!parsed || typeof parsed !== 'object') return null;
  const payload = parsed as { text?: unknown; linkPreview?: unknown };
  if (typeof payload.text !== 'string') return null;
  const preview = sanitizeLinkPreview(payload.linkPreview);
  if (!preview) return null;
  return { preview, text: payload.text.slice(0, MAX_TEXT_LENGTH) };
}

export function serializeLinkPreviewMessage(text: string, preview: LinkPreview): string {
  return JSON.stringify({ text: text.slice(0, MAX_TEXT_LENGTH), linkPreview: preview });
}

export function linkPreviewText(content: string): string | null {
  const message = parseLinkPreviewMessage(content);
  if (!message) return null;
  return message.text.trim() ? message.text : message.preview.url;
}

export function firstHttpUrl(text: string): string | null {
  const match = FIRST_URL.exec(text);
  if (!match) return null;
  const url = match[1];
  return HTTP_URL.test(url) ? url : null;
}

export function buildLinkPreviewPayload(text: string, preview: LinkPreview | null): string {
  if (!preview) return text;
  const full = serializeLinkPreviewMessage(text, preview);
  if (full.length <= MAX_SERIALIZED_PREVIEW_CHARS) return full;
  if (!preview.imageBase64) return text;
  const { imageBase64: _image, ...withoutImage } = preview;
  const bare = serializeLinkPreviewMessage(text, withoutImage);
  return bare.length <= MAX_SERIALIZED_PREVIEW_CHARS ? bare : text;
}
