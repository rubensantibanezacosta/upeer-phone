import { StyleSheet, View } from 'react-native';
import { UserPlus, UserRound } from '@tamagui/lucide-icons-2';
import { Text } from 'tamagui';

import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { ProfileAvatar } from '@/components/profile-avatar';
import type { ContactCard } from '@/domain/contact-card';
import { useTheme } from '@/hooks/use-theme';

type ChatContactCardProps = {
  card: ContactCard;
  saved: boolean;
  onPress: (card: ContactCard) => void;
  tone?: 'mine' | 'theirs';
};

export function ChatContactCard({ card, saved, onPress, tone = 'theirs' }: ChatContactCardProps) {
  const theme = useTheme();
  const titleColor = tone === 'mine' ? theme.bubbleMineText : theme.text;
  const detailColor = tone === 'mine' ? theme.bubbleMineText : theme.placeholder;
  const Icon = saved ? UserRound : UserPlus;

  return (
    <NativeRipplePressable
      accessibilityHint={saved ? 'Abre la ficha del contacto' : 'Añade el contacto a tu agenda'}
      accessibilityLabel={`Contacto compartido: ${card.name || card.upeerId.slice(0, 12)}`}
      accessibilityRole="button"
      onPress={() => onPress(card)}
      style={[styles.card, { backgroundColor: theme.background, borderColor: theme.border }]}
    >
      <ProfileAvatar avatar={card.avatar} name={card.name || '?'} size={40} />
      <View style={styles.body}>
        <Text color={titleColor} fontSize={13} fontWeight="700" numberOfLines={1}>{card.name || card.upeerId.slice(0, 12)}</Text>
        <Text color={detailColor} ellipsizeMode="middle" fontFamily="monospace" fontSize={10} numberOfLines={1}>{card.upeerId}</Text>
        <Text color={detailColor} fontSize={10}>{saved ? 'En tu agenda' : 'Añadir a contactos'}</Text>
      </View>
      <Icon color={detailColor} size={16} />
    </NativeRipplePressable>
  );
}

const styles = StyleSheet.create({
  card: {
    alignItems: 'center',
    borderRadius: 10,
    borderWidth: StyleSheet.hairlineWidth,
    flexDirection: 'row',
    gap: 10,
    minWidth: 190,
    overflow: 'hidden',
    paddingHorizontal: 10,
    paddingVertical: 8,
  },
  body: { flex: 1, gap: 1, minWidth: 110 },
});
