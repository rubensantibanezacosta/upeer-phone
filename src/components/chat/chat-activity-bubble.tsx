import { useEffect, useState } from 'react';
import { Animated, Easing, StyleSheet, View } from 'react-native';
import { Mic } from '@tamagui/lucide-icons-2';

import { activityBubbleOf } from '@/domain/activity-bubble';
import type { ConversationActivity } from '@/domain/chat-preview';
import { useTheme } from '@/hooks/use-theme';

const DOT_DELAYS = [0, 160, 320];
const CYCLE_MS = 1200;
const RISE_MS = 240;
const FALL_MS = 480;
const PULSE_MS = 700;

function useDots() {
  const [values] = useState(() => DOT_DELAYS.map(() => new Animated.Value(0)));
  useEffect(() => {
    const loops = values.map((value, index) => Animated.loop(Animated.sequence([
      Animated.delay(DOT_DELAYS[index]),
      Animated.timing(value, { duration: RISE_MS, easing: Easing.out(Easing.quad), toValue: 1, useNativeDriver: true }),
      Animated.timing(value, { duration: FALL_MS, easing: Easing.in(Easing.quad), toValue: 0, useNativeDriver: true }),
      Animated.delay(CYCLE_MS - RISE_MS - FALL_MS - DOT_DELAYS[index]),
    ])));
    loops.forEach((loop) => loop.start());
    return () => loops.forEach((loop) => loop.stop());
  }, [values]);
  return values;
}

function usePulse() {
  const [value] = useState(() => new Animated.Value(0));
  useEffect(() => {
    const loop = Animated.loop(Animated.sequence([
      Animated.timing(value, { duration: PULSE_MS, easing: Easing.out(Easing.quad), toValue: 1, useNativeDriver: true }),
      Animated.timing(value, { duration: PULSE_MS, easing: Easing.in(Easing.quad), toValue: 0, useNativeDriver: true }),
    ]));
    loop.start();
    return () => loop.stop();
  }, [value]);
  return value;
}

function TypingDots({ color }: { color: string }) {
  const dots = useDots();
  return (
    <View style={styles.dots}>
      {dots.map((dot, index) => {
        const translateY = dot.interpolate({ inputRange: [0, 1], outputRange: [0, -3] });
        const opacity = dot.interpolate({ inputRange: [0, 1], outputRange: [0.35, 1] });
        return <Animated.View key={index} style={[styles.dot, { backgroundColor: color, opacity, transform: [{ translateY }] }]} />;
      })}
    </View>
  );
}

function RecordingMic({ color, background }: { color: string; background: string }) {
  const pulse = usePulse();
  const scale = pulse.interpolate({ inputRange: [0, 1], outputRange: [0.72, 1.5] });
  const opacity = pulse.interpolate({ inputRange: [0, 1], outputRange: [0.55, 0] });
  return (
    <View style={styles.mic}>
      <Animated.View style={[styles.micRing, { borderColor: color, opacity, transform: [{ scale }] }]} />
      <View style={[styles.micCircle, { backgroundColor: background }]}>
        <Mic color={color} size={14} strokeWidth={1.5} />
      </View>
    </View>
  );
}

export function ChatActivityBubble({ activity }: { activity: ConversationActivity | '' }) {
  const theme = useTheme();
  const bubble = activityBubbleOf(activity);
  if (!bubble) return null;
  const recording = bubble.kind === 'recording';
  return (
    <View
      accessibilityLabel={bubble.label}
      accessibilityLiveRegion="polite"
      accessible
      style={styles.row}
      testID="chat-activity-bubble"
    >
      <View style={[styles.bubble, recording ? styles.bubbleRecording : styles.bubbleTyping, { backgroundColor: theme.backgroundElement }]}>
        {recording
          ? <RecordingMic background={`${theme.danger}1F`} color={theme.danger} />
          : <TypingDots color={theme.placeholder} />}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  bubble: {
    alignItems: 'center',
    borderRadius: 12,
    elevation: 2,
    justifyContent: 'center',
    shadowColor: '#000',
    shadowOffset: { height: 2, width: 0 },
    shadowOpacity: 0.35,
    shadowRadius: 8,
  },
  bubbleRecording: { height: 40, minWidth: 52, paddingHorizontal: 12 },
  bubbleTyping: { height: 34, minWidth: 58, paddingHorizontal: 12 },
  dot: { borderRadius: 3.5, height: 7, width: 7 },
  dots: { alignItems: 'center', flexDirection: 'row', gap: 4 },
  mic: { alignItems: 'center', height: 26, justifyContent: 'center', width: 26 },
  micCircle: { alignItems: 'center', borderRadius: 11, height: 22, justifyContent: 'center', width: 22 },
  micRing: { borderRadius: 13, borderWidth: 2, height: 26, position: 'absolute', width: 26 },
  row: { flexDirection: 'row', justifyContent: 'flex-start', marginVertical: 6, width: '100%' },
});
