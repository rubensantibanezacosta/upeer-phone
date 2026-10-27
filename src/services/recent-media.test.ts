import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MediaType } from 'expo-media-library';

import {
  ensureMediaPermission,
  formatDuration,
  loadRecentMedia,
  mediaKind,
  recentMediaAttachment,
  recentMediaItems,
  toRecentMediaItem,
} from './recent-media';

const media = vi.hoisted(() => ({
  broken: [] as string[],
  granted: true,
  metadata: [] as unknown[],
  requested: 0,
  uri: 'file:///recientes/asset.mp4',
}));

vi.mock('expo-document-picker', () => ({ getDocumentAsync: async () => ({ canceled: true }) }));
vi.mock('expo-image-picker', () => ({ launchCameraAsync: async () => ({ canceled: true }), launchImageLibraryAsync: async () => ({ canceled: true }) }));

vi.mock('expo-media-library', () => ({
  Asset: class {
    private readonly id: string;

    constructor(id: string) {
      this.id = id;
    }

    async getUri() {
      if (media.broken.includes(this.id)) throw new Error('asset perdido');
      return media.uri;
    }

    toString() {
      return this.id;
    }
  },
  AssetField: { CREATION_TIME: 'creationTime', MEDIA_TYPE: 'mediaType' },
  MediaType: { IMAGE: 'photo', VIDEO: 'video' },
  Query: class {
    within() {
      return this;
    }

    orderBy() {
      return this;
    }

    limit() {
      return this;
    }

    async exeForMetadata() {
      return media.metadata;
    }
  },
  getPermissionsAsync: async () => ({ granted: media.granted }),
  requestPermissionsAsync: async () => {
    media.requested += 1;
    return { granted: media.granted };
  },
}));

const photo = { duration: 0, filename: 'IMG_1.jpg', id: 'a1', mediaType: MediaType.IMAGE };
const video = { duration: 75.4, filename: 'VID_2.mp4', id: 'a2', mediaType: MediaType.VIDEO };

beforeEach(() => {
  media.broken = [];
  media.granted = true;
  media.metadata = [];
  media.requested = 0;
  media.uri = 'file:///recientes/asset.mp4';
});

describe('mediaKind', () => {
  it('marca fotos y vídeos y descarta el resto', () => {
    expect(mediaKind('photo' as never)).toBe('image');
    expect(mediaKind('video' as never)).toBe('video');
    expect(mediaKind('audio' as never)).toBeNull();
  });
});

describe('formatDuration', () => {
  it('formatea segundos como minutos y segundos', () => {
    expect(formatDuration(75)).toBe('1:15');
    expect(formatDuration(9.6)).toBe('0:10');
    expect(formatDuration(600)).toBe('10:00');
  });

  it('devuelve vacío sin duración', () => {
    expect(formatDuration(0)).toBe('');
    expect(formatDuration(null)).toBe('');
    expect(formatDuration(undefined)).toBe('');
    expect(formatDuration(-3)).toBe('');
  });
});

describe('toRecentMediaItem', () => {
  it('convierte una foto sin duración', () => {
    expect(toRecentMediaItem(photo)).toEqual({ duration: null, id: 'a1', kind: 'image', title: 'IMG_1.jpg' });
  });

  it('conserva la duración de los vídeos', () => {
    expect(toRecentMediaItem(video)).toMatchObject({ duration: 75.4, kind: 'video', title: 'VID_2.mp4' });
  });

  it('pone título por defecto y descarta elementos sin id', () => {
    expect(toRecentMediaItem({ ...video, filename: null })).toMatchObject({ title: 'Vídeo' });
    expect(toRecentMediaItem({ ...photo, filename: null })).toMatchObject({ title: 'Foto' });
    expect(toRecentMediaItem({ ...photo, id: '' })).toBeNull();
  });
});

describe('recentMediaItems', () => {
  it('quita duplicados conservando el orden', () => {
    const items = recentMediaItems([photo, { ...photo, filename: 'copia.jpg' }, video]);
    expect(items.map((item) => item.id)).toEqual(['a1', 'a2']);
    expect(items[0].title).toBe('IMG_1.jpg');
  });

  it('descarta tipos no soportados', () => {
    expect(recentMediaItems([{ ...photo, mediaType: 'audio' as never }])).toEqual([]);
  });
});

describe('ensureMediaPermission', () => {
  it('no pide permiso si ya está concedido', async () => {
    await expect(ensureMediaPermission()).resolves.toBe('granted');
    expect(media.requested).toBe(0);
  });

  it('pide permiso cuando falta y devuelve el resultado', async () => {
    media.granted = false;
    await expect(ensureMediaPermission()).resolves.toBe('denied');
    expect(media.requested).toBe(1);
  });
});

describe('loadRecentMedia', () => {
  it('devuelve la bandeja con permiso concedido', async () => {
    media.metadata = [photo, video];
    await expect(loadRecentMedia()).resolves.toEqual([
      { duration: null, id: 'a1', kind: 'image', title: 'IMG_1.jpg', uri: 'file:///recientes/asset.mp4' },
      { duration: 75.4, id: 'a2', kind: 'video', title: 'VID_2.mp4', uri: 'file:///recientes/asset.mp4' },
    ]);
  });

  it('descarta las fotos cuya miniatura no se puede resolver', async () => {
    media.metadata = [photo, video];
    media.broken = ['a1'];
    await expect(loadRecentMedia()).resolves.toMatchObject([{ id: 'a2' }]);
  });

  it('no consulta nada sin permiso', async () => {
    media.granted = false;
    media.metadata = [photo];
    await expect(loadRecentMedia()).resolves.toEqual([]);
  });
});

describe('recentMediaAttachment', () => {
  it('resuelve el archivo real del asset', async () => {
    const attachment = await recentMediaAttachment({ duration: 12, id: 'a2', kind: 'video', title: 'VID_2.mp4', uri: 'file:///recientes/asset.mp4' });
    expect(attachment).toEqual({ fileName: 'VID_2.mp4', mimeType: 'video/mp4', path: '/recientes/asset.mp4' });
  });

  it('pone un nombre por defecto si el asset no lo tenía', async () => {
    const attachment = await recentMediaAttachment({ duration: null, id: 'a1', kind: 'image', title: '  ', uri: 'file:///recientes/asset.mp4' });
    expect(attachment).toMatchObject({ fileName: 'foto.jpg', mimeType: 'image/jpeg' });
  });
});
