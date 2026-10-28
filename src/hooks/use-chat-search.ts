import { useCallback, useEffect, useRef, useState } from 'react';
import { BackHandler } from 'react-native';

import {
  appendChatSearchResults,
  CHAT_SEARCH_PAGE_SIZE,
  resolveSearchStep,
  searchPageHasMore,
  toChatSearchResults,
  type ChatSearchResult,
} from '@/domain/chat-search';
import { searchChatMessages } from '../../modules/upeer-transport/src/UPeerTransportModule';

type UseChatSearchOptions = {
  chatId: string;
  onJump: (messageID: string, timestamp: number) => void;
};

const DEBOUNCE_MS = 250;

export function useChatSearch({ chatId, onJump }: UseChatSearchOptions) {
  const [active, setActive] = useState(false);
  const [query, setQuery] = useState('');
  const [index, setIndex] = useState(0);
  const [results, setResults] = useState<ChatSearchResult[]>([]);
  const [hasMore, setHasMore] = useState(false);
  const [loading, setLoading] = useState(false);
  const tokenRef = useRef(0);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const pagingRef = useRef(false);

  const clearTimer = useCallback(() => {
    if (timerRef.current) clearTimeout(timerRef.current);
    timerRef.current = null;
  }, []);

  useEffect(() => clearTimer, [clearTimer]);

  const jump = useCallback((page: ChatSearchResult[], position: number) => {
    const match = page[position];
    if (match) onJump(match.id, match.timestamp);
  }, [onJump]);

  const reset = useCallback(() => {
    tokenRef.current += 1;
    clearTimer();
    setResults([]);
    setHasMore(false);
    setIndex(0);
    setLoading(false);
  }, [clearTimer]);

  const runSearch = useCallback((term: string) => {
    const token = ++tokenRef.current;
    setLoading(true);
    void (async () => {
      try {
        const hits = await searchChatMessages(chatId, term, CHAT_SEARCH_PAGE_SIZE, 0);
        if (tokenRef.current !== token) return;
        const page = toChatSearchResults(hits);
        setResults(page);
        setHasMore(searchPageHasMore(hits.length));
        setIndex(0);
        setLoading(false);
        jump(page, 0);
      } catch (cause) {
        console.warn('No se pudo buscar en la conversación.', cause);
        if (tokenRef.current !== token) return;
        setResults([]);
        setHasMore(false);
        setIndex(0);
        setLoading(false);
      }
    })();
  }, [chatId, jump]);

  const open = useCallback(() => {
    setActive(true);
    setQuery('');
    reset();
  }, [reset]);

  const close = useCallback(() => {
    setActive(false);
    setQuery('');
    reset();
  }, [reset]);

  const changeQuery = useCallback((value: string) => {
    setQuery(value);
    reset();
    const term = value.trim();
    if (!term) return;
    timerRef.current = setTimeout(() => runSearch(term), DEBOUNCE_MS);
  }, [reset, runSearch]);

  const loadNextPage = useCallback(() => {
    if (pagingRef.current) return;
    const term = query.trim();
    const token = tokenRef.current;
    const offset = results.length;
    pagingRef.current = true;
    setLoading(true);
    void (async () => {
      try {
        const hits = await searchChatMessages(chatId, term, CHAT_SEARCH_PAGE_SIZE, offset);
        if (tokenRef.current !== token) return;
        const page = appendChatSearchResults(results, toChatSearchResults(hits));
        setResults(page);
        setHasMore(searchPageHasMore(hits.length));
        setIndex(offset);
        setLoading(false);
        jump(page, offset);
      } catch (cause) {
        console.warn('No se pudo continuar la búsqueda en la conversación.', cause);
        if (tokenRef.current !== token) return;
        setLoading(false);
      } finally {
        pagingRef.current = false;
      }
    })();
  }, [chatId, jump, query, results]);

  const step = useCallback((delta: number) => {
    const action = resolveSearchStep(index, results.length, hasMore, delta);
    if (action.kind === 'none') return;
    if (action.kind === 'load') {
      loadNextPage();
      return;
    }
    setIndex(action.index);
    jump(results, action.index);
  }, [hasMore, index, jump, loadNextPage, results]);

  useEffect(() => {
    if (!active) return;
    const subscription = BackHandler.addEventListener('hardwareBackPress', () => {
      close();
      return true;
    });
    return () => subscription.remove();
  }, [active, close]);

  return {
    active,
    changeQuery,
    close,
    count: results.length,
    hasMore,
    highlightedID: results[index]?.id,
    index,
    loading,
    open,
    query,
    step,
  };
}
