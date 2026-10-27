import { describe, expect, it } from 'vitest';

import { buildLinkPreviewPayload, firstHttpUrl, linkPreviewText, parseLinkPreviewMessage, sanitizeLinkPreview, serializeLinkPreviewMessage, urlHostname } from './link-preview';

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

  it('saca la primera url http del texto', () => {
    expect(firstHttpUrl('mira https://upeer.chat/a y https://upeer.chat/b')).toBe('https://upeer.chat/a');
    expect(firstHttpUrl('sin enlaces')).toBeNull();
    expect(firstHttpUrl('no vale ftp://upeer.chat')).toBeNull();
  });

  it('manda el texto pelado cuando no hay vista previa', () => {
    expect(buildLinkPreviewPayload('hola', null)).toBe('hola');
  });

  it('manda el sobre cuando hay vista previa', () => {
    const payload = buildLinkPreviewPayload('mira', { domain: 'upeer.chat', title: 'Docs', url: URL_PAGE });
    expect(parseLinkPreviewMessage(payload)?.preview.title).toBe('Docs');
    expect(parseLinkPreviewMessage(payload)?.text).toBe('mira');
  });

  it('suelta la imagen y luego la vista previa si el sobre no cabe', () => {
    const image = `data:image/webp;base64,${'A'.repeat(200 * 1024)}`;
    const withImage = buildLinkPreviewPayload('mira', { imageBase64: image, title: 'Docs', url: URL_PAGE });
    expect(withImage.includes('imageBase64')).toBe(false);
    const huge = { description: 'd'.repeat(400), title: 't'.repeat(200), url: `https://upeer.chat/${'p'.repeat(50_000)}` };
    expect(buildLinkPreviewPayload('mira', huge)).toBe('mira');
  });
});
