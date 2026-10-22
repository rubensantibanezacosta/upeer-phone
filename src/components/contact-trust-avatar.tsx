import { StyleSheet, View } from 'react-native';
import { BadgeAlert, BadgeCheck, CircleCheck, Shield, Sparkles } from '@tamagui/lucide-icons-2';

import { ProfileAvatar } from '@/components/profile-avatar';
import { getTrustDescriptor, type TrustKind } from '@/domain/trust';
import { useTheme } from '@/hooks/use-theme';

function TrustIcon({ color, kind }: { color: string; kind: TrustKind }) {
  if (kind === 'alert') return <BadgeAlert color={color} size={12} strokeWidth={2.3} />;
  if (kind === 'verified') return <BadgeCheck color={color} size={12} strokeWidth={2.3} />;
  if (kind === 'check') return <CircleCheck color={color} size={12} strokeWidth={2.3} />;
  if (kind === 'new') return <Sparkles color={color} size={12} strokeWidth={2.3} />;
  return <Shield color={color} size={12} strokeWidth={2.3} />;
}

export function ContactTrustAvatar({ avatar, name, size = 96, vouchScore }: {
  avatar?: string;
  name: string;
  size?: number;
  vouchScore?: number;
}) {
  const theme = useTheme();
  const trust = getTrustDescriptor(vouchScore);
  const color = trust.kind === 'alert'
    ? theme.danger
    : trust.kind === 'verified'
      ? theme.success
      : trust.kind === 'check'
        ? theme.accent
        : theme.placeholder;

  return (
    <View style={{ height: size, width: size }}>
      <ProfileAvatar avatar={avatar} name={name} size={size} />
      <View pointerEvents="none" style={[styles.badge, { backgroundColor: theme.background, borderColor: theme.border }]}>
        <TrustIcon color={color} kind={trust.kind} />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  badge: {
    alignItems: 'center',
    borderRadius: 6,
    borderWidth: 1,
    bottom: -2,
    height: 20,
    justifyContent: 'center',
    position: 'absolute',
    right: -2,
    width: 20,
  },
});
