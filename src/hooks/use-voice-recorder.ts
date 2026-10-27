import { useCallback, useState } from 'react';
import { AudioModule, RecordingPresets, setAudioModeAsync, useAudioRecorder, useAudioRecorderState } from 'expo-audio';

import { voiceNoteFileName, VOICE_NOTE_MIME_TYPE } from '@/domain/voice-note';
import { pickedAttachment, type PickedAttachment } from '@/services/attachment-picker';

const STATUS_INTERVAL_MS = 250;

export type VoiceActivity = 'recording' | 'stop';

export type VoiceRecorder = {
  cancel: () => void;
  recording: boolean;
  seconds: number;
  start: () => Promise<void>;
  stop: () => Promise<PickedAttachment | null>;
};

export function useVoiceRecorder(
  onError: (message: string) => void,
  onActivity: (activity: VoiceActivity) => void = () => undefined,
): VoiceRecorder {
  const recorder = useAudioRecorder(RecordingPresets.HIGH_QUALITY);
  const status = useAudioRecorderState(recorder, STATUS_INTERVAL_MS);
  const [recording, setRecording] = useState(false);

  const start = useCallback(async () => {
    const permission = await AudioModule.requestRecordingPermissionsAsync();
    if (!permission.granted) {
      onError('Para grabar una nota de voz hace falta permiso de micrófono');
      return;
    }
    try {
      await setAudioModeAsync({ allowsRecording: true, playsInSilentMode: true });
      await recorder.prepareToRecordAsync();
      recorder.record();
      setRecording(true);
      onActivity('recording');
    } catch (cause) {
      console.warn('No se pudo empezar a grabar la nota de voz.', cause);
      onError('No se pudo empezar a grabar');
    }
  }, [onActivity, onError, recorder]);

  const release = useCallback(async () => {
    await setAudioModeAsync({ allowsRecording: false, playsInSilentMode: true });
  }, []);

  const stop = useCallback(async (): Promise<PickedAttachment | null> => {
    setRecording(false);
    onActivity('stop');
    try {
      await recorder.stop();
    } catch (cause) {
      console.warn('No se pudo cerrar la grabación.', cause);
    }
    await release();
    const uri = recorder.uri;
    if (!uri) return null;
    return pickedAttachment(uri, voiceNoteFileName(), VOICE_NOTE_MIME_TYPE);
  }, [onActivity, recorder, release]);

  const cancel = useCallback(() => {
    setRecording(false);
    onActivity('stop');
    recorder.stop().catch((cause: unknown) => {
      console.warn('No se pudo descartar la grabación.', cause);
    }).finally(() => {
      void release();
    });
  }, [onActivity, recorder, release]);

  return { cancel, recording, seconds: status.durationMillis / 1000, start, stop };
}
