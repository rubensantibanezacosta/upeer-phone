import { useState } from 'react';
import { Images } from '@tamagui/lucide-icons-2';

import { InteractiveRow } from '@/components/contact-info/interactive-row';
import { SharedMediaStrip } from '@/components/chat/shared-media-strip';
import { MediaViewer } from '@/components/media/media-viewer';
import { useSharedMedia } from '@/hooks/use-shared-media';
import type { AttachmentInfo } from '@/domain/attachment';

export function SharedMediaRow({ chatId, refreshKey }: { chatId: string; refreshKey?: number }) {
  const items = useSharedMedia({ chatId, refreshKey });
  const [open, setOpen] = useState(false);
  const [viewed, setViewed] = useState<AttachmentInfo | null>(null);

  return <>
    <InteractiveRow
      chevron={items.length > 0}
      hint={items.length > 0 ? String(items.length) : undefined}
      icon={Images}
      label="Multimedia"
      onPress={() => setOpen((current) => !current)}
    />
    {open ? <SharedMediaStrip items={items} onOpen={(item) => setViewed(item.attachment)} /> : null}
    <MediaViewer attachment={viewed} onClose={() => setViewed(null)} />
  </>;
}
