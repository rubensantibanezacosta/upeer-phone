import { useEffect, useState } from 'react';
import { Animated, Easing, View } from 'react-native';
import { formatBadgeCount } from '@/domain/chat-unread';
import { UnreadCountBadge } from '@/components/unread-count-badge';

const DURATION = 180;
const ICON_SHIFT = -2;

type TabIconWithBadgeProps = {
  children: React.ReactNode;
  count: number;
};

export function TabIconWithBadge({ children, count }: TabIconWithBadgeProps) {
  const label = formatBadgeCount(count);
  const visible = label !== undefined;
  const [progress] = useState(() => new Animated.Value(visible ? 1 : 0));

  useEffect(() => {
    Animated.timing(progress, {
      duration: DURATION,
      easing: Easing.out(Easing.quad),
      toValue: visible ? 1 : 0,
      useNativeDriver: true,
    }).start();
  }, [progress, visible]);

  const translateX = progress.interpolate({ inputRange: [0, 1], outputRange: [0, ICON_SHIFT] });
  const scale = progress.interpolate({ inputRange: [0, 1], outputRange: [0.6, 1] });

  return (
    <View style={styles.container}>
      <Animated.View style={{ transform: [{ translateX }] }}>{children}</Animated.View>
      <Animated.View pointerEvents="none" style={[styles.layer, { opacity: progress, transform: [{ scale }] }]}>
        <UnreadCountBadge count={count} />
      </Animated.View>
    </View>
  );
}

const styles = {
  container: { alignItems: 'center' as const, justifyContent: 'center' as const },
  layer: { alignItems: 'flex-end' as const, bottom: 0, justifyContent: 'center' as const, left: 0, position: 'absolute' as const, right: -8, top: 0 },
};
