import { useCallback, useEffect, useRef, useState } from 'react';

import { firstHttpUrl, type LinkPreview } from '@/domain/link-preview';
import { fetchLinkPreview } from '@/services/link-preview-fetch';

const DEBOUNCE_MS = 600;

type Snapshot = {
  loading: boolean;
  preview: LinkPreview | null;
  url: string;
};

const EMPTY: Snapshot = { loading: false, preview: null, url: '' };

export function useLinkPreview(text: string) {
  const url = firstHttpUrl(text) ?? '';
  const [snapshot, setSnapshot] = useState<Snapshot>(EMPTY);
  const requested = useRef('');
  const dismissed = useRef('');

  useEffect(() => {
    if (!url || url === requested.current || url === dismissed.current) return;
    const controller = new AbortController();
    const timer = setTimeout(() => {
      requested.current = url;
      setSnapshot((current) => ({ ...current, loading: true }));
      fetchLinkPreview(url, controller.signal)
        .then((preview) => {
          if (controller.signal.aborted) return;
          setSnapshot({ loading: false, preview, url });
        })
        .catch((cause: unknown) => {
          console.warn('No se pudo preparar la vista previa del enlace.', cause);
          if (!controller.signal.aborted) setSnapshot({ loading: false, preview: null, url });
        });
    }, DEBOUNCE_MS);
    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [url]);

  const dismiss = useCallback(() => {
    dismissed.current = url;
    setSnapshot({ loading: false, preview: null, url });
  }, [url]);

  const clear = useCallback(() => {
    requested.current = '';
    dismissed.current = '';
    setSnapshot(EMPTY);
  }, []);

  return {
    clear,
    dismiss,
    loading: url !== '' && snapshot.loading,
    preview: url !== '' && snapshot.url === url ? snapshot.preview : null,
  };
}
