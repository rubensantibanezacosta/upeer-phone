import { describe, expect, it } from 'vitest';

import {
  formatVoiceDuration,
  VOICE_NOTE_EXTENSION,
  VOICE_NOTE_MIME_TYPE,
  voiceNoteFileName,
  voiceNoteTooShortMessage,
  voiceNoteUsable,
} from './voice-note';

describe('voiceNoteFileName', () => {
  it('incluye la marca de tiempo y la extensión del contenedor', () => {
    expect(voiceNoteFileName(1700000000000)).toBe(`voice_note_1700000000000.${VOICE_NOTE_EXTENSION}`);
    expect(VOICE_NOTE_MIME_TYPE).toBe('audio/mp4');
  });
});

describe('formatVoiceDuration', () => {
  it('formatea segundos como minutos y segundos', () => {
    expect(formatVoiceDuration(0)).toBe('0:00');
    expect(formatVoiceDuration(7)).toBe('0:07');
    expect(formatVoiceDuration(59.9)).toBe('0:59');
    expect(formatVoiceDuration(60)).toBe('1:00');
    expect(formatVoiceDuration(185)).toBe('3:05');
  });

  it('nunca muestra valores negativos', () => {
    expect(formatVoiceDuration(-4)).toBe('0:00');
  });
});

describe('voiceNoteUsable', () => {
  it('exige al menos un segundo de grabación', () => {
    expect(voiceNoteUsable(0.4)).toBe(false);
    expect(voiceNoteUsable(0.99)).toBe(false);
    expect(voiceNoteUsable(1)).toBe(true);
    expect(voiceNoteUsable(3.2)).toBe(true);
  });
});

describe('voiceNoteTooShortMessage', () => {
  it('explica el mínimo en segundos', () => {
    expect(voiceNoteTooShortMessage()).toContain('1 s');
  });
});
