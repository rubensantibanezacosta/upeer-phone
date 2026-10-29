import { useEffect, useState } from 'react';
import { Animated, Easing } from 'react-native';
import Svg, { Circle, G } from 'react-native-svg';

import { INDETERMINATE_PERCENT, ringGeometry } from '@/domain/circular-progress';

const SPIN_MS = 800;

type CircularProgressProps = {
  color: string;
  trackColor: string;
  determinate?: boolean;
  value?: number;
  size?: number;
  thickness?: number;
};

export function CircularProgress({
  color,
  trackColor,
  determinate = false,
  value = 0,
  size = 40,
  thickness = 6,
}: CircularProgressProps) {
  const [spin] = useState(() => new Animated.Value(0));
  const geometry = ringGeometry(size, thickness, determinate ? value : INDETERMINATE_PERCENT);

  useEffect(() => {
    if (determinate) {
      spin.setValue(0);
      return;
    }
    const loop = Animated.loop(
      Animated.timing(spin, { duration: SPIN_MS, easing: Easing.linear, toValue: 1, useNativeDriver: true }),
    );
    loop.start();
    return () => {
      loop.stop();
      spin.setValue(0);
    };
  }, [determinate, spin]);

  const rotate = spin.interpolate({ inputRange: [0, 1], outputRange: ['0deg', '360deg'] });

  return (
    <Animated.View
      accessibilityRole="progressbar"
      accessibilityValue={determinate ? { max: 100, min: 0, now: Math.round(geometry.percent) } : undefined}
      style={{ height: size, transform: determinate ? undefined : [{ rotate }], width: size }}
    >
      <Svg height={size} width={size}>
        <G origin={`${geometry.center}, ${geometry.center}`} rotation={-90}>
          <Circle cx={geometry.center} cy={geometry.center} fill="none" r={geometry.radius} stroke={trackColor} strokeWidth={thickness} />
          <Circle
            cx={geometry.center}
            cy={geometry.center}
            fill="none"
            r={geometry.radius}
            stroke={color}
            strokeDasharray={geometry.circumference}
            strokeDashoffset={geometry.dashOffset}
            strokeLinecap="round"
            strokeWidth={thickness}
          />
        </G>
      </Svg>
    </Animated.View>
  );
}
