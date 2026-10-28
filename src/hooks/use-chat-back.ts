import { useAndroidBack } from '@/hooks/use-android-back';
import { resolveChatBack } from '@/domain/chat-back';

type ChatBackTargets = {
  cancelComposing: () => void;
  cancelForwarding: () => void;
  closeCard: () => void;
  closeGroup: () => void;
  closeMenu: () => void;
  closeSearch: () => void;
  closeSheet: () => void;
  exitSelection: () => void;
};

type ChatBackState = {
  cardOpen: boolean;
  composing: boolean;
  forwarding: boolean;
  groupOpen: boolean;
  menuOpen: boolean;
  searchActive: boolean;
  selecting: boolean;
  sheetOpen: boolean;
};

export function useChatBack(state: ChatBackState, targets: ChatBackTargets): void {
  useAndroidBack(() => {
    const action = resolveChatBack(state);
    if (action === 'close-menu') { targets.closeMenu(); return true; }
    if (action === 'close-search') { targets.closeSearch(); return true; }
    if (action === 'close-sheet') { targets.closeSheet(); return true; }
    if (action === 'close-card') { targets.closeCard(); return true; }
    if (action === 'close-group') { targets.closeGroup(); return true; }
    if (action === 'cancel-forwarding') { targets.cancelForwarding(); return true; }
    if (action === 'cancel-composing') { targets.cancelComposing(); return true; }
    if (action === 'exit-selection') { targets.exitSelection(); return true; }
    return false;
  });
}
