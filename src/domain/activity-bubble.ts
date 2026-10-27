import type { ConversationActivity } from '@/domain/chat-preview';

export type ActivityBubble = {
  kind: ConversationActivity;
  label: string;
};

const BUBBLES: Record<ConversationActivity, ActivityBubble> = {
  recording: { kind: 'recording', label: 'Grabando nota de voz' },
  typing: { kind: 'typing', label: 'Escribiendo' },
};

export function activityBubbleOf(activity: ConversationActivity | '' | undefined): ActivityBubble | null {
  if (activity !== 'typing' && activity !== 'recording') return null;
  return BUBBLES[activity];
}
