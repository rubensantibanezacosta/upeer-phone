import { useEffect, useRef } from 'react';
import { BackHandler } from 'react-native';

export type BackHandlerAction = () => boolean;

export function useAndroidBack(handler: BackHandlerAction, enabled = true): void {
  const latest = useRef<BackHandlerAction>(handler);

  useEffect(() => {
    latest.current = handler;
  }, [handler]);

  useEffect(() => {
    if (!enabled) return;
    const subscription = BackHandler.addEventListener('hardwareBackPress', () => latest.current());
    return () => subscription.remove();
  }, [enabled]);
}
