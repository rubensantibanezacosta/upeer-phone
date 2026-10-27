import { beforeEach, describe, expect, it, vi } from 'vitest';

import { clearThumbnailCache, thumbnailKey, videoThumbnail } from './video-thumbnail';

const native = vi.hoisted(() => ({
  fail: false,
  calls: [] as { options?: { quality?: number; time?: number }; uri: string }[],
}));

vi.mock('expo-video-thumbnails', () => ({
  getThumbnailAsync: async (uri: string, options?: { quality?: number; time?: number }) => {
    native.calls.push({ options, uri });
    if (native.fail) throw new Error('códec no soportado');
    return { height: 90, uri: `${uri}#thumb`, width: 160 };
  },
}));


beforeEach(() => {
  native.calls = [];
  native.fail = false;
  clearThumbnailCache();
});

describe('thumbnailKey', () => {
  it('distingue el instante y la calidad', () => {
    expect(thumbnailKey('file:///a.mp4')).toBe('file:///a.mp4@500@0.6');
    expect(thumbnailKey('file:///a.mp4', { quality: 0.3, time: 1000 })).toBe('file:///a.mp4@1000@0.3');
  });
});

describe('videoThumbnail', () => {
  it('devuelve la miniatura generada', async () => {
    await expect(videoThumbnail('file:///a.mp4')).resolves.toBe('file:///a.mp4#thumb');
    expect(native.calls[0]).toEqual({ options: { quality: 0.6, time: 500 }, uri: 'file:///a.mp4' });
  });

  it('no genera nada sin uri', async () => {
    await expect(videoThumbnail(null)).resolves.toBeNull();
    await expect(videoThumbnail(undefined)).resolves.toBeNull();
    expect(native.calls).toHaveLength(0);
  });

  it('reutiliza el resultado para la misma petición', async () => {
    await videoThumbnail('file:///b.mp4');
    await videoThumbnail('file:///b.mp4');
    expect(native.calls).toHaveLength(1);
  });

  it('vuelve a intentarlo tras un fallo', async () => {
    native.fail = true;
    await expect(videoThumbnail('file:///c.mp4')).resolves.toBeNull();
    native.fail = false;
    await expect(videoThumbnail('file:///c.mp4')).resolves.toBe('file:///c.mp4#thumb');
    expect(native.calls).toHaveLength(2);
  });
});
