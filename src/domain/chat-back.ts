export type ChatBackState = {
  menuOpen: boolean;
  searchActive: boolean;
  sheetOpen: boolean;
  cardOpen: boolean;
  groupOpen: boolean;
  forwarding: boolean;
  composing: boolean;
};

export type ChatBackAction =
  | 'close-menu'
  | 'close-search'
  | 'close-sheet'
  | 'close-card'
  | 'close-group'
  | 'cancel-forwarding'
  | 'cancel-composing'
  | 'leave';

export function resolveChatBack(state: ChatBackState): ChatBackAction {
  if (state.menuOpen) return 'close-menu';
  if (state.searchActive) return 'close-search';
  if (state.sheetOpen) return 'close-sheet';
  if (state.cardOpen) return 'close-card';
  if (state.groupOpen) return 'close-group';
  if (state.forwarding) return 'cancel-forwarding';
  if (state.composing) return 'cancel-composing';
  return 'leave';
}
