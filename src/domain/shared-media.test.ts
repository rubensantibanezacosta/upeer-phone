import { describe, expect, it } from 'vitest';

import { collectSharedMedia, sharedMediaCount } from './shared-media';

function media(id: string, fileId: string, fileName: string, extra: Record<string, unknown> = {}) {
  return {
    id,
    timestamp: Number(id.replace(/\D/g, '')) || 1,
    content: JSON.stringify({ type: 'file', fileId, fileName, mimeType: 'image/jpeg', ...extra }),
  };
}

describe('collectSharedMedia', () => {
  it('coge solo imágenes y vídeos', () => {
    const items = collectSharedMedia([
      media('1', 'a', 'foto.jpg'),
      { id: '2', timestamp: 2, content: JSON.stringify({ type: 'file', fileId: 'b', fileName: 'doc.pdf', mimeType: 'application/pdf' }) },
      { id: '3', timestamp: 3, content: 'hola' },
    ]);

    expect(items).toHaveLength(1);
    expect(items[0].fileId).toBe('a');
  });

  it('marca los vídeos', () => {
    const items = collectSharedMedia([
      { id: '1', timestamp: 1, content: JSON.stringify({ type: 'file', fileId: 'v', fileName: 'clip.mp4', mimeType: 'video/mp4' }) },
    ]);

    expect(items[0].isVideo).toBe(true);
  });

  it('no repite el mismo fichero aunque salga en dos mensajes', () => {
    const items = collectSharedMedia([media('1', 'a', 'foto.jpg'), media('2', 'a', 'foto.jpg')]);

    expect(items).toHaveLength(1);
  });

  it('respeta el limite', () => {
    const messages = Array.from({ length: 10 }, (_, index) => media(String(index + 1), `f${index}`, `f${index}.jpg`));

    expect(collectSharedMedia(messages, 3)).toHaveLength(3);
  });

  it('usa la miniatura como preview y la ruta local en las imágenes', () => {
    const [withThumb, withPath] = collectSharedMedia([
      media('1', 'a', 'foto.jpg', { thumbnail: 'data:image/jpeg;base64,AAA' }),
      media('2', 'b', 'foto2.jpg', { savedPath: '/datos/foto2.jpg' }),
    ]);

    expect(withThumb.preview).toBe('data:image/jpeg;base64,AAA');
    expect(withPath.preview).toBe('/datos/foto2.jpg');
  });

  it('un vídeo sin miniatura no tiene preview', () => {
    const [item] = collectSharedMedia([
      { id: '1', timestamp: 1, content: JSON.stringify({ type: 'file', fileId: 'v', fileName: 'clip.mp4', mimeType: 'video/mp4', savedPath: '/v/clip.mp4' }) },
    ]);

    expect(item.preview).toBeUndefined();
  });
});

describe('sharedMediaCount', () => {
  it('cuenta todos los multimedia, no solo los cinco primeros', () => {
    const messages = Array.from({ length: 8 }, (_, index) => media(String(index + 1), `f${index}`, `f${index}.jpg`));

    expect(sharedMediaCount(messages)).toBe(8);
  });
});
