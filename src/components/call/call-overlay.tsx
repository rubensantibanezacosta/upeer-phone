import { useEffect, useState } from 'react';

import { ActiveCallOverlay } from '@/components/call/active-call-overlay';
import { IncomingCallSheet } from '@/components/call/incoming-call-sheet';
import { isCallOver, shouldRing, shouldShowCall } from '@/domain/call-session';
import { useCallSession } from '@/hooks/use-call-session';
import { getContacts } from '@/storage/contact-store';
import { getConversations } from '../../../modules/upeer-transport/src/UPeerTransportModule';

type PeerCard = { title: string; avatar?: string };

const EMPTY_CARD: PeerCard = { title: '' };

export function CallOverlay() {
  const { call, accept, reject, hangUp, toggleMute, toggleCamera } = useCallSession();
  const [resolved, setResolved] = useState<{ id: string; card: PeerCard } | null>(null);
  const [dismissed, setDismissed] = useState<string | null>(null);
  const peerUpeerId = call?.peerUpeerId ?? '';

  useEffect(() => {
    if (!peerUpeerId) return;
    let cancelled = false;
    const load = async () => {
      try {
        const [conversations, contacts] = await Promise.all([getConversations(), getContacts()]);
        if (cancelled) return;
        const conversation = conversations.find((item) => item.upeerId === peerUpeerId);
        const contact = contacts.find((item) => item.upeerId === peerUpeerId);
        setResolved({
          id: peerUpeerId,
          card: {
            title: contact?.name?.trim() || conversation?.alias?.trim() || '',
            avatar: conversation?.avatar,
          },
        });
      } catch (cause) {
        console.warn('No se pudo resolver el nombre de la llamada.', cause);
      }
    };
    void load();
    return () => { cancelled = true; };
  }, [peerUpeerId]);

  if (!shouldShowCall(call) || !call) return null;
  if (dismissed === call.callId && isCallOver(call)) return null;

  const card = resolved && resolved.id === peerUpeerId ? resolved.card : EMPTY_CARD;
  const name = card.title || call.peerUpeerId.slice(0, 12);
  if (shouldRing(call)) {
    return <IncomingCallSheet avatar={card.avatar} call={call} onAccept={accept} onReject={reject} peerName={name} />;
  }
  return (
    <ActiveCallOverlay
      avatar={card.avatar}
      call={call}
      onHangUp={() => { setDismissed(call.callId); hangUp(); }}
      onToggleCamera={toggleCamera}
      onToggleMute={toggleMute}
      peerName={name}
    />
  );
}
