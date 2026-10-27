import { describe, expect, it } from 'vitest';

import { absoluteUrl, decodeHtmlEntities, extractMeta, extractTitle, imageCandidates, parseLinkPreviewHtml } from './link-preview-html';

const URL_PAGE = 'https://upeer.chat/blog/post?id=7';

const HTML = `<!doctype html><html><head>
<title>Blog de uPeer</title>
<meta property="og:title" content="Novedades &amp; m&#225;s" />
<meta property="og:description" content="Lo que viene" />
<meta property="og:image" content="/img/cover.png" />
<link rel="icon" href="/favicon-32.png" sizes="32x32" />
<link rel="apple-touch-icon" href="/touch-180.png" sizes="180x180" />
</head><body></body></html>`;

describe('decodeHtmlEntities', () => {
  it('resuelve las entidades habituales y las numéricas', () => {
    expect(decodeHtmlEntities('a &amp; b &lt;c&gt; &quot;d&quot; &#039;e&#39;')).toBe('a & b <c> "d" \'e\'');
    expect(decodeHtmlEntities('caf&#233;')).toBe('café');
    expect(decodeHtmlEntities('roto &#999999999;')).toBe('roto &#999999999;');
  });
});

describe('absoluteUrl', () => {
  it('deja pasar las url absolutas', () => {
    expect(absoluteUrl(URL_PAGE, 'https://cdn.example/a.png')).toBe('https://cdn.example/a.png');
  });

  it('resuelve relativas contra el origen', () => {
    expect(absoluteUrl(URL_PAGE, '/img/cover.png')).toBe('https://upeer.chat/img/cover.png');
    expect(absoluteUrl(URL_PAGE, 'cover.png')).toBe('https://upeer.chat/blog/cover.png');
    expect(absoluteUrl(URL_PAGE, '../shared/cover.png')).toBe('https://upeer.chat/shared/cover.png');
    expect(absoluteUrl(URL_PAGE, '//cdn.example/a.png')).toBe('https://cdn.example/a.png');
  });

  it('descarta esquemas que no son http', () => {
    expect(absoluteUrl(URL_PAGE, 'data:image/png;base64,AAAA')).toBe('');
    expect(absoluteUrl('no-es-url', '/a.png')).toBe('');
  });
});

describe('extractMeta y extractTitle', () => {
  it('lee el contenido por propiedad y decodifica', () => {
    expect(extractMeta(HTML, 'og:title')).toBe('Novedades & más');
    expect(extractMeta(HTML, 'description')).toBeUndefined();
    expect(extractTitle(HTML)).toBe('Blog de uPeer');
  });
});

describe('imageCandidates', () => {
  it('ordena og:image antes que los iconos y los deja absolutos', () => {
    const candidates = imageCandidates(HTML, URL_PAGE);
    expect(candidates[0]).toBe('https://upeer.chat/img/cover.png');
    expect(candidates).toContain('https://upeer.chat/touch-180.png');
    expect(candidates).toContain('https://upeer.chat/favicon-32.png');
    expect(candidates.filter((url) => url.endsWith('/favicon.ico'))).toHaveLength(1);
  });

  it('no repite candidatos', () => {
    const html = '<meta property="og:image" content="/a.png"><meta name="twitter:image" content="https://upeer.chat/a.png">';
    expect(imageCandidates(html, URL_PAGE)).toEqual(['https://upeer.chat/a.png', 'https://upeer.chat/favicon.ico']);
  });
});

describe('parseLinkPreviewHtml', () => {
  it('compone la vista previa con título, descripción y dominio', () => {
    const parsed = parseLinkPreviewHtml(HTML, URL_PAGE);
    expect(parsed?.preview).toEqual({
      description: 'Lo que viene',
      domain: 'upeer.chat',
      title: 'Novedades & más',
      url: URL_PAGE,
    });
    expect(parsed?.images[0]).toBe('https://upeer.chat/img/cover.png');
  });

  it('cae al título del documento y a twitter: si falta og', () => {
    const html = '<title>Hola</title><meta name="twitter:description" content="desde twitter">';
    expect(parseLinkPreviewHtml(html, URL_PAGE)?.preview.title).toBe('Hola');
  });

  it('descarta páginas sin título ni descripción', () => {
    expect(parseLinkPreviewHtml('<html><body>hola</body></html>', URL_PAGE)).toBeNull();
  });

  it('recorta los textos largos', () => {
    const html = `<meta property="og:title" content="${'t'.repeat(500)}"><meta property="og:description" content="${'d'.repeat(900)}">`;
    const preview = parseLinkPreviewHtml(html, URL_PAGE)?.preview;
    expect(preview?.title).toHaveLength(200);
    expect(preview?.description).toHaveLength(400);
  });
});
