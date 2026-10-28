import { StyleSheet, View } from 'react-native';
import { BadgeAlert } from '@tamagui/lucide-icons-2';

import { ProfileAvatar } from '@/components/profile-avatar';
import { isLowReputation } from '@/domain/trust';
import { useTheme } from '@/hooks/use-theme';

export function ContactTrustAvatar({ avatar, name, size = 96, vouchScore }: {
  avatar?: string;
  name: string;
  size?: number;
  vouchScore?: number;
}) {
  const theme = useTheme();

  return (
    <View style={{ height: size, width: size }}>
      <ProfileAvatar avatar={avatar} name={name} size={size} />
      {isLowReputation(vouchScore) ? (
        <View pointerEvents="none" style={[styles.badge, { backgroundColor: theme.background, borderColor: theme.border }]}>
          <BadgeAlert color={theme.danger} size={12} strokeWidth={2.3} />
        </View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  badge: {
    alignItems: 'center',
    borderRadius: 10,
    borderWidth: 1,
    bottom: -2,
    height: 20,
    justifyContent: 'center',
    position: 'absolute',
    right: -2,
    width: 20,
  },
});
