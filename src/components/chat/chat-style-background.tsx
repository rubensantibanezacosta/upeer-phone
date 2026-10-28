import { memo, useMemo } from 'react';
import { StyleSheet, View } from 'react-native';
import Svg, { Defs, Line, RadialGradient, Rect, Stop } from 'react-native-svg';

import { getChatStylePreset } from '@/domain/chat-style';

type ChatStyleBackgroundProps = {
  styleId?: string | null;
};

const STRIPE_COUNT = 40;

export const ChatStyleBackground = memo(function ChatStyleBackground({ styleId }: ChatStyleBackgroundProps) {
  const preset = getChatStylePreset(styleId);
  const stripes = useMemo(() => {
    if (!preset.stripes) return [];
    return Array.from({ length: STRIPE_COUNT }, (_, index) => index);
  }, [preset.stripes]);

  if (preset.tints.length === 0 && stripes.length === 0) return null;

  return (
    <View pointerEvents="none" style={StyleSheet.absoluteFill}>
      <Svg height="100%" preserveAspectRatio="none" viewBox="0 0 100 100" width="100%">
        <Defs>
          {preset.tints.map((tint, index) => (
            <RadialGradient cx={`${tint.cx}%`} cy={`${tint.cy}%`} fx={`${tint.cx}%`} fy={`${tint.cy}%`} id={`tint-${index}`} key={`tint-${index}`} rx={`${tint.rx}%`} ry={`${tint.ry}%`}>
              <Stop offset="0%" stopColor={tint.color} />
              <Stop offset="100%" stopColor={tint.color.replace(/[\d.]+\)$/, '0)')} />
            </RadialGradient>
          ))}
        </Defs>
        {preset.tints.map((_, index) => (
          <Rect fill={`url(#tint-${index})`} height="100" key={`rect-${index}`} width="100" x="0" y="0" />
        ))}
        {preset.stripes
          ? stripes.map((index) => (
            <Line key={`stripe-${index}`} stroke={preset.stripes?.color} strokeWidth="1" x1={-20 + index * 3} x2={20 + index * 3} y1="-20" y2="120" />
          ))
          : null}
      </Svg>
    </View>
  );
});
