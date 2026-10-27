import { useEffect, useState } from 'react';

import { videoThumbnail } from '@/services/video-thumbnail';

export function useVideoThumbnail(uri: string | null | undefined): string | null {
  const [thumbnail, setThumbnail] = useState<string | null>(null);

  useEffect(() => {
    if (!uri) return;
    let alive = true;
    void videoThumbnail(uri).then((result) => {
      if (alive) setThumbnail(result);
    });
    return () => {
      alive = false;
    };
  }, [uri]);

  return thumbnail;
}
