import type { ComponentProps } from 'react';

import { ChatComposerArea } from '@/components/chat/chat-composer-area';
import { ChatSearchBar } from '@/components/chat/chat-search-bar';
import type { useChatSearch } from '@/hooks/use-chat-search';

type ChatInputAreaProps = {
  composer: ComponentProps<typeof ChatComposerArea>;
  search: ReturnType<typeof useChatSearch>;
};

export function ChatInputArea({ composer, search }: ChatInputAreaProps) {
  if (!search.active) return <ChatComposerArea {...composer} />;
  return (
    <ChatSearchBar
      hasMore={search.hasMore}
      index={search.index}
      loading={search.loading}
      matches={search.count}
      onChangeQuery={search.changeQuery}
      onClose={search.close}
      onNext={() => search.step(1)}
      onPrevious={() => search.step(-1)}
      query={search.query}
    />
  );
}
