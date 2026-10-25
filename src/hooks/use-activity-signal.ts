import { useCallback, useEffect, useRef } from 'react';
import { AppState } from 'react-native';

import { ACTIVITY_HEARTBEAT_MS, shouldReportActivity, type ActivityTrigger } from '@/domain/activity-signal';
import { markUserActive } from '../../modules/upeer-transport/src/UPeerTransportModule';

export function useActivitySignal(): { reportTouch: () => void } {
  const lastReportedAt = useRef(0);

  const report = useCallback((trigger: ActivityTrigger) => {
    const now = Date.now();
    if (!shouldReportActivity(trigger, lastReportedAt.current, now)) return;
    lastReportedAt.current = now;
    markUserActive().catch((cause: unknown) => console.warn('No se pudo anunciar la actividad del usuario.', cause));
  }, []);

  useEffect(() => {
    report('resume');
    const subscription = AppState.addEventListener('change', (state) => {
      if (state === 'active') report('resume');
    });
    const heartbeat = setInterval(() => {
      if (AppState.currentState === 'active') report('heartbeat');
    }, ACTIVITY_HEARTBEAT_MS);
    return () => {
      subscription.remove();
      clearInterval(heartbeat);
    };
  }, [report]);

  const reportTouch = useCallback(() => report('gesture'), [report]);

  return { reportTouch };
}
