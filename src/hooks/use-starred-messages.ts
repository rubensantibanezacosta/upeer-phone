import { useCallback, useEffect, useSyncExternalStore } from 'react';

import { collectStarred, isStarred, type StarredMap } from '@/domain/starred';
import { getStarred, loadStarred, setStarredMessages, subscribeStarred, toggleStarredMessage } from '@/storage/starred-store';
import type { ProtocolMessage } from '../../modules/upeer-transport/src/UPeerTransport.types';

export function useStarredMessages(messages: readonly ProtocolMessage[] = []) {
  const map: StarredMap = useSyncExternalStore(subscribeStarred, getStarred, getStarred);

  useEffect(() => {
    void loadStarred();
  }, []);

  const starred = collectStarred(messages, map);
  const toggle = useCallback((id: string): Promise<void> => toggleStarredMessage(id), []);
  const setMany = useCallback((ids: readonly string[], value: boolean): Promise<void> => setStarredMessages(ids, value), []);
  const isMarked = useCallback((id?: string | null): boolean => isStarred(map, id), [map]);

  return { isMarked, map, setMany, starred, toggle };
}
