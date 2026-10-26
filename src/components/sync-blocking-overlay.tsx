import { useEffect, useState } from 'react';
import { Animated, Easing, StyleSheet, View } from 'react-native';
import { CheckCircle2, Router } from '@tamagui/lucide-icons-2';
import { SizableText, Text, YStack } from 'tamagui';

import { useAndroidBack } from '@/hooks/use-android-back';
import { useSyncStatus } from '@/hooks/use-sync-status';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

const FADE_IN_MS = 220;
const FADE_OUT_MS = 700;

export function SyncBlockingOverlay() {
  const theme = useTheme();
  const scale = useTextScale();
  const status = useSyncStatus();
  const syncing = status.state === 'syncing';
  const [opacity] = useState(() => new Animated.Value(0));
  const [spin] = useState(() => new Animated.Value(0));

  useEffect(() => {
    Animated.timing(opacity, {
      duration: syncing ? FADE_IN_MS : FADE_OUT_MS,
      easing: Easing.out(Easing.quad),
      toValue: syncing ? 1 : 0,
      useNativeDriver: true,
    }).start();
  }, [opacity, syncing]);

  useEffect(() => {
    if (!syncing) return;
    const loop = Animated.loop(Animated.timing(spin, { duration: 1100, easing: Easing.linear, toValue: 1, useNativeDriver: true }));
    loop.start();
    return () => {
      loop.stop();
      spin.setValue(0);
    };
  }, [spin, syncing]);

  useAndroidBack(() => true, syncing);

  const finished = !syncing;
  const progress = status.progress;
  const determinate = progress?.determinate === true;
  const percent = Math.round(progress?.percent ?? 0);
  const detail = progress?.detail ?? 'Conectando con tus dispositivos';
  const headline = progress?.headline ?? 'Sincronizando tus dispositivos…';
  const width = finished ? 100 : determinate ? Math.max(4, Math.min(100, percent)) : 30;

  return (
    <Animated.View pointerEvents={syncing ? 'auto' : 'none'} style={[styles.root, { backgroundColor: theme.background, opacity }]}>
      <View style={styles.ring}>
        {finished ? <CheckCircle2 color={theme.accent} size={48} strokeWidth={1.8} /> : (
          <>
            <Animated.View style={[styles.spinner, { borderColor: theme.backgroundElement, borderTopColor: theme.accent, transform: [{ rotate: spin.interpolate({ inputRange: [0, 1], outputRange: ['0deg', '360deg'] }) }] }]} />
            <Router color={theme.accent} size={40} strokeWidth={1.8} />
          </>
        )}
      </View>
      <YStack alignItems="center" gap={6} maxWidth={320}>
        <SizableText color={theme.text} fontSize={scale(20)} fontWeight="700">uPeer</SizableText>
        <Text color={finished ? theme.accent : theme.placeholder} fontSize={scale(13)} textAlign="center">
          {finished ? '¡Al día!' : headline}
        </Text>
      </YStack>
      <View style={[styles.track, { backgroundColor: theme.backgroundElement }]}>
        <View style={[styles.fill, { backgroundColor: theme.accent, width: `${width}%` }]} />
      </View>
      <Text color={theme.placeholder} fontSize={scale(12)} textAlign="center">
        {finished ? 'Los chats ya están disponibles.' : `${detail}. Los chats aparecerán en cuanto termine.`}
      </Text>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  root: { alignItems: 'center', bottom: 0, elevation: 30, gap: 18, justifyContent: 'center', left: 0, paddingHorizontal: 32, position: 'absolute', right: 0, top: 0, zIndex: 9999 },
  ring: { alignItems: 'center', height: 80, justifyContent: 'center', width: 80 },
  spinner: { borderRadius: 40, borderWidth: 3, bottom: 0, left: 0, position: 'absolute', right: 0, top: 0 },
  track: { borderRadius: 4, height: 6, overflow: 'hidden', width: 200 },
  fill: { borderRadius: 4, height: 6 },
});
