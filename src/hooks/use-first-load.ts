import { useCallback, useState } from 'react';

export type FirstLoad = {
  loading: boolean;
  markLoaded: () => void;
};

export function useFirstLoad(): FirstLoad {
  const [loaded, setLoaded] = useState(false);
  const markLoaded = useCallback(() => setLoaded(true), []);
  return { loading: !loaded, markLoaded };
}
