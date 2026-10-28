import { useState, type ComponentProps } from 'react';

import { ChatCalendarSheet } from '@/components/chat/chat-calendar-sheet';
import { ChatComposerArea } from '@/components/chat/chat-composer-area';
import { ChatSearchBar } from '@/components/chat/chat-search-bar';
import { useSnackbar } from '@/components/snackbar';
import type { useChatSearch } from '@/hooks/use-chat-search';

type ChatInputAreaProps = {
  composer: ComponentProps<typeof ChatComposerArea>;
  search: ReturnType<typeof useChatSearch>;
};

export function ChatInputArea({ composer, search }: ChatInputAreaProps) {
  const { showSnackbar } = useSnackbar();
  const [calendarOpen, setCalendarOpen] = useState(false);

  if (!search.active) return <ChatComposerArea {...composer} />;

  const pickDay = (timestamp: number) => {
    void search.jumpToDay(timestamp).then((result) => {
      if (result === 'empty') showSnackbar('No hay mensajes ese día');
      if (result === 'failed') showSnackbar('No se pudo abrir ese día');
    });
  };

  return (
    <>
      <ChatSearchBar
        hasMore={search.hasMore}
        index={search.index}
        loading={search.loading}
        matches={search.count}
        onChangeQuery={search.changeQuery}
        onClose={search.close}
        onNext={() => search.step(1)}
        onOpenCalendar={() => setCalendarOpen(true)}
        onPrevious={() => search.step(-1)}
        query={search.query}
      />
      <ChatCalendarSheet onClose={() => setCalendarOpen(false)} onPickDay={pickDay} visible={calendarOpen} />
    </>
  );
}
