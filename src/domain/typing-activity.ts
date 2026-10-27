export const TYPING_INTERVAL_MS = 3000;

export type TypingSignal = 'typing' | 'stop' | null;

export type TypingSignalInput = {
  hasText: boolean;
  lastSentAt: number;
  now: number;
};

export function decideTypingSignal({ hasText, lastSentAt, now }: TypingSignalInput): TypingSignal {
  if (!hasText) return lastSentAt > 0 ? 'stop' : null;
  if (now - lastSentAt < TYPING_INTERVAL_MS) return null;
  return 'typing';
}
