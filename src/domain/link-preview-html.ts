import type { LinkPreview } from '@/domain/link-preview';
import { urlHostname } from '@/domain/link-preview';

const MAX_TITLE_LENGTH = 200;
const MAX_DESCRIPTION_LENGTH = 400;
const META_TAG = /<meta\s[^>]+>/gi;
const PROPERTY = /(?:property|name)=["']([^"']+)["']/i;
const CONTENT = /content=["']([^"']*)["']/i;
const HTTP_PREFIX = /^https?:\/\//i;
const OTHER_SCHEME = /^[a-z][a-z0-9+.-]*:/i;

export type ParsedLinkPreview = {
  images: string[];
  preview: LinkPreview;
};

export function decodeHtmlEntities(text: string): string {
  return text
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#0?39;|&apos;/g, "'")
    .replace(/&#(\d+);/g, (match, code: string) => numericEntity(code) ?? match)
    .replace(/&amp;/g, '&');
}

function numericEntity(code: string): string | null {
  const value = Number(code);
  if (!Number.isFinite(value) || value < 32 || value > 0x10ffff) return null;
  return String.fromCodePoint(value);
}

export function originOf(url: string): string {
  const match = /^(https?:\/\/[^/?#]+)/i.exec(url);
  return match ? match[1] : '';
}

export function absoluteUrl(base: string, ref: string): string {
  const value = ref.trim();
  if (!value) return '';
  if (HTTP_PREFIX.test(value)) return value;
  if (OTHER_SCHEME.test(value)) return '';
  const origin = originOf(base);
  if (!origin) return '';
  if (value.startsWith('//')) return `${origin.slice(0, origin.indexOf(':') + 1)}${value}`;
  if (value.startsWith('/')) return collapse(`${origin}${value}`);
  const path = (base.split(/[?#]/, 1)[0] ?? base).replace(origin, '');
  const dir = path.endsWith('/') ? path : path.slice(0, path.lastIndexOf('/') + 1);
  return collapse(`${origin}${dir}${value}`);
}

function collapse(url: string): string {
  const origin = originOf(url);
  if (!origin) return url;
  const parts: string[] = [];
  for (const part of url.slice(origin.length).split('/')) {
    if (!part || part === '.') continue;
    if (part === '..') parts.pop();
    else parts.push(part);
  }
  return `${origin}/${parts.join('/')}`;
}

export function extractMeta(html: string, property: string): string | undefined {
  const tags = html.match(META_TAG);
  if (!tags) return undefined;
  for (const tag of tags) {
    const name = PROPERTY.exec(tag);
    if (!name || name[1].toLowerCase() !== property.toLowerCase()) continue;
    const content = CONTENT.exec(tag);
    if (content) return decodeHtmlEntities(content[1].trim());
  }
  return undefined;
}

export function extractTitle(html: string): string | undefined {
  const match = /<title[^>]*>([^<]+)<\/title>/i.exec(html);
  return match ? decodeHtmlEntities(match[1].trim()) : undefined;
}

function jsonLdImage(html: string): string | undefined {
  const scripts = html.matchAll(/<script[^>]+application\/ld\+json[^>]*>([\s\S]*?)<\/script>/gi);
  for (const script of scripts) {
    try {
      const data = JSON.parse(script[1]) as { image?: unknown; logo?: { url?: unknown } };
      const image = data.image ?? data.logo?.url;
      if (typeof image === 'string' && image.startsWith('http')) return image;
      if (Array.isArray(image) && typeof image[0] === 'string') return image[0];
    } catch (cause) {
      console.warn('Se ignoró un bloque JSON-LD ilegible de la vista previa.', cause);
    }
  }
  return undefined;
}

function iconCandidates(html: string): string[] {
  const direct: string[] = [];
  const scored: { href: string; size: number }[] = [];
  for (const tag of html.match(/<link\s[^>]+>/gi) ?? []) {
    const rel = /rel=["']([^"']*)["']/i.exec(tag);
    const href = /href=["']([^"']+)["']/i.exec(tag);
    if (!rel || !href) continue;
    const kind = rel[1].toLowerCase();
    if (kind === 'image_src') {
      direct.push(href[1]);
      continue;
    }
    if (!kind.includes('icon') || kind.includes('mask-icon')) continue;
    const size = /sizes=["'](\d+)/i.exec(tag);
    scored.push({ href: href[1], size: size ? Number(size[1]) : 32 });
  }
  return [...direct, ...scored.sort((a, b) => b.size - a.size).map((item) => item.href)];
}

export function imageCandidates(html: string, base: string): string[] {
  const found: string[] = [];
  const add = (ref: string | undefined) => {
    if (!ref) return;
    const url = absoluteUrl(base, ref);
    if (url && !found.includes(url)) found.push(url);
  };
  add(extractMeta(html, 'og:image:secure_url'));
  add(extractMeta(html, 'og:image'));
  add(extractMeta(html, 'og:image:url'));
  add(extractMeta(html, 'twitter:image'));
  add(extractMeta(html, 'twitter:image:src'));
  add(jsonLdImage(html));
  for (const icon of iconCandidates(html)) add(icon);
  add(extractMeta(html, 'msapplication-TileImage'));
  add(extractMeta(html, 'thumbnail'));
  const origin = originOf(base);
  if (origin) add(`${origin}/favicon.ico`);
  return found;
}

export function parseLinkPreviewHtml(html: string, url: string): ParsedLinkPreview | null {
  const title = extractMeta(html, 'og:title') ?? extractMeta(html, 'twitter:title') ?? extractTitle(html);
  const description = extractMeta(html, 'og:description')
    ?? extractMeta(html, 'twitter:description')
    ?? extractMeta(html, 'description');
  if (!title && !description) return null;
  return {
    images: imageCandidates(html, url),
    preview: {
      url,
      title: title?.slice(0, MAX_TITLE_LENGTH),
      description: description?.slice(0, MAX_DESCRIPTION_LENGTH),
      domain: urlHostname(url),
    },
  };
}
