import { useCallback, useEffect, useMemo, useState } from 'react';
import { BackHandler } from 'react-native';

import { searchChatMessages, type ChatSearchResult } from '@/domain/chat-search';
import type { ProtocolMessage } from '../../modules/upeer-transport/src/UPeerTransport.types';

type UseChatSearchOptions = {
  messages: readonly ProtocolMessage[];
  onJump: (messageID: string) => void;
};

export function useChatSearch({ messages, onJump }: UseChatSearchOptions) {
  const [active, setActive] = useState(false);
  const [query, setQuery] = useState('');
  const [index, setIndex] = useState(0);
  const matches = useMemo(() => searchChatMessages(messages, query), [messages, query]);

  const jumpTo = useCallback((results: ChatSearchResult[], position: number) => {
    const match = results[(position + results.length) % results.length];
    if (match) onJump(match.id);
  }, [onJump]);

  const open = useCallback(() => {
    setActive(true);
    setQuery('');
    setIndex(0);
  }, []);

  const close = useCallback(() => {
    setActive(false);
    setQuery('');
    setIndex(0);
  }, []);

  const changeQuery = useCallback((value: string) => {
    setQuery(value);
    setIndex(0);
    jumpTo(searchChatMessages(messages, value), 0);
  }, [jumpTo, messages]);

  const step = useCallback((delta: number) => {
    if (matches.length === 0) return;
    const next = (index + delta + matches.length) % matches.length;
    setIndex(next);
    jumpTo(matches, next);
  }, [index, jumpTo, matches]);

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
    count: matches.length,
    highlightedID: matches[index]?.id,
    index,
    open,
    query,
    step,
  };
}
