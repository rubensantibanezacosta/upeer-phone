import { describe, expect, it } from 'vitest';

import { linkPreviewText, parseLinkPreviewMessage, sanitizeLinkPreview, serializeLinkPreviewMessage, urlHostname } from './link-preview';

const URL_PAGE = 'https://upeer.chat/docs?x=1';

describe('link preview', () => {
  it('deduce el host de la url', () => {
    expect(urlHostname(URL_PAGE)).toBe('upeer.chat');
    expect(urlHostname('https://Example.COM:8443/a')).toBe('example.com:8443');
    expect(urlHostname('no-es-una-url')).toBe('no-es-una-url');
  });

  it('descarta previsualizaciones sin url http', () => {
    expect(sanitizeLinkPreview(null)).toBeNull();
    expect(sanitizeLinkPreview({ title: 'sin url' })).toBeNull();
    expect(sanitizeLinkPreview({ url: 'javascript:alert(1)' })).toBeNull();
  });

  it('recorta titulo y descripcion y rellena el dominio', () => {
    const preview = sanitizeLinkPreview({ url: URL_PAGE, title: 'x'.repeat(300), description: 'y'.repeat(500) });
    expect(preview?.domain).toBe('upeer.chat');
    expect(preview?.title).toHaveLength(200);
    expect(preview?.description).toHaveLength(400);
  });

  it('solo acepta imagenes de datos dentro del limite', () => {
    const small = `data:image/png;base64,${'A'.repeat(32)}`;
    expect(sanitizeLinkPreview({ url: URL_PAGE, imageBase64: small })?.imageBase64).toBe(small);
    expect(sanitizeLinkPreview({ url: URL_PAGE, imageBase64: 'https://upeer.chat/a.png' })?.imageBase64).toBeUndefined();
    expect(sanitizeLinkPreview({ url: URL_PAGE, imageBase64: `data:image/png;base64,${'A'.repeat(300 * 1024)}` })?.imageBase64).toBeUndefined();
  });

  it('lee el sobre que manda el escritorio', () => {
    const content = JSON.stringify({ text: 'mira esto https://upeer.chat/docs?x=1', linkPreview: { url: URL_PAGE, title: 'Documentación' } });
    const parsed = parseLinkPreviewMessage(content);
    expect(parsed?.text).toBe('mira esto https://upeer.chat/docs?x=1');
    expect(parsed?.preview.title).toBe('Documentación');
    expect(parsed?.preview.domain).toBe('upeer.chat');
  });

  it('no confunde otros sobres json con una previsualizacion', () => {
    expect(parseLinkPreviewMessage('hola')).toBeNull();
    expect(parseLinkPreviewMessage(JSON.stringify({ type: 'file', fileName: 'a.pdf' }))).toBeNull();
    expect(parseLinkPreviewMessage(JSON.stringify({ text: 'sin preview' }))).toBeNull();
    expect(parseLinkPreviewMessage(JSON.stringify({ text: 'sin url', linkPreview: { title: 'x' } }))).toBeNull();
    expect(parseLinkPreviewMessage('{ roto')).toBeNull();
  });

  it('conserva el texto al serializar y volver a leer', () => {
    const content = serializeLinkPreviewMessage('enlace', { url: URL_PAGE, domain: 'upeer.chat' });
    expect(linkPreviewText(content)).toBe('enlace');
  });

  it('usa la url como texto cuando el mensaje va vacio', () => {
    expect(linkPreviewText(serializeLinkPreviewMessage('   ', { url: URL_PAGE }))).toBe(URL_PAGE);
    expect(linkPreviewText('hola')).toBeNull();
  });
});
