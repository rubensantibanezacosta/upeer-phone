export const VOICE_NOTE_MIN_SECONDS = 1;
export const VOICE_NOTE_EXTENSION = 'm4a';
export const VOICE_NOTE_MIME_TYPE = 'audio/mp4';

export function voiceNoteFileName(timestamp = Date.now()): string {
  return `voice_note_${timestamp}.${VOICE_NOTE_EXTENSION}`;
}

export function formatVoiceDuration(seconds: number): string {
  const total = Math.max(0, Math.floor(seconds));
  const minutes = Math.floor(total / 60);
  const rest = total % 60;
  return `${minutes}:${String(rest).padStart(2, '0')}`;
}

export function voiceNoteUsable(seconds: number): boolean {
  return seconds >= VOICE_NOTE_MIN_SECONDS;
}

export function voiceNoteTooShortMessage(): string {
  return `Mantén pulsado al menos ${VOICE_NOTE_MIN_SECONDS} s para grabar una nota de voz`;
}
