import { Image, StyleSheet, Text, View } from 'react-native';

import { useTheme } from '@/hooks/use-theme';

export function ProfileAvatar({ avatar, name, size = 96, borderColor = 'transparent', borderWidth = 0 }: {
  avatar?: string;
  name: string;
  size?: number;
  borderColor?: string;
  borderWidth?: number;
}) {
  const theme = useTheme();
  const initial = (name || '?').charAt(0).toUpperCase() || '?';
  const fontSize = Math.round(size * 0.42);
  return (
    <View style={[styles.avatar, { backgroundColor: theme.backgroundSelected, borderColor, borderWidth, borderRadius: size / 2, height: size, width: size }]}>
      {avatar ? <Image resizeMode="cover" source={{ uri: avatar }} style={styles.image} /> : <Text style={[styles.initial, { color: theme.text, fontSize, lineHeight: Math.round(fontSize * 1.35) }]}>{initial}</Text>}
    </View>
  );
}

const styles = StyleSheet.create({ avatar: { alignItems: 'center', justifyContent: 'center', overflow: 'hidden' }, image: { height: '100%', width: '100%' }, initial: { fontWeight: '700', textAlign: 'center' } });
