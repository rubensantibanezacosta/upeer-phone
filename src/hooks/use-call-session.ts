import { useCallback, useEffect, useRef, useState } from 'react';

import type { CallState } from '../../modules/upeer-transport/src/UPeerTransportModule';
import {
  acceptCall,
  endCall,
  getCallState,
  rejectCall,
  setCallCamera,
  setCallMute,
  startCall,
} from '../../modules/upeer-transport/src/UPeerTransportModule';

const POLL_MS = 700;

export type CallActions = {
  call: CallState | null;
  accept: () => void;
  reject: () => void;
  hangUp: () => void;
  toggleMute: () => void;
  toggleCamera: () => void;
  start: (upeerId: string, kind: 'audio' | 'video') => Promise<boolean>;
};

export function useCallSession(): CallActions {
  const [call, setCall] = useState<CallState | null>(null);
  const alive = useRef(true);

  useEffect(() => {
    alive.current = true;
    const read = async () => {
      try {
        const next = await getCallState();
        if (alive.current) setCall(next);
      } catch (cause) {
        console.warn('No se pudo leer el estado de la llamada.', cause);
      }
    };
    void read();
    const timer = setInterval(() => { void read(); }, POLL_MS);
    return () => {
      alive.current = false;
      clearInterval(timer);
    };
  }, []);

  const run = useCallback((action: (callId: string) => Promise<unknown>) => {
    if (!call) return;
    const callId = call.callId;
    action(callId).catch((cause: unknown) => {
      console.warn('La acción sobre la llamada no se pudo completar.', cause);
    });
  }, [call]);

  const accept = useCallback(() => run(acceptCall), [run]);
  const reject = useCallback(() => run(rejectCall), [run]);
  const hangUp = useCallback(() => run(endCall), [run]);
  const toggleMute = useCallback(() => run((callId) => setCallMute(callId, !call?.muted)), [run, call?.muted]);
  const toggleCamera = useCallback(() => run((callId) => setCallCamera(callId, !call?.cameraEnabled)), [run, call?.cameraEnabled]);

  const start = useCallback(async (upeerId: string, kind: 'audio' | 'video'): Promise<boolean> => {
    try {
      const callId = await startCall(upeerId, kind);
      return callId.length > 0;
    } catch (cause) {
      console.warn('No se pudo iniciar la llamada.', cause);
      return false;
    }
  }, []);

  return { call, accept, reject, hangUp, toggleMute, toggleCamera, start };
}
