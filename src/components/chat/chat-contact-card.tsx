import { StyleSheet, View } from 'react-native';
import { ChevronRight } from '@tamagui/lucide-icons-2';
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

  return (
    <NativeRipplePressable
      accessibilityHint="Abre la tarjeta para guardar el contacto"
      accessibilityLabel={`Tarjeta de contacto: ${card.name || card.upeerId.slice(0, 12)}`}
      accessibilityRole="button"
      onPress={() => onPress(card)}
      style={[styles.card, { backgroundColor: theme.background, borderColor: theme.border }]}
    >
      <ProfileAvatar avatar={card.avatar} name={card.name || '?'} size={44} />
      <View style={styles.body}>
        <Text color={titleColor} fontSize={13} fontWeight="700" numberOfLines={1}>{card.name || card.upeerId.slice(0, 12)}</Text>
        <Text color={detailColor} fontSize={11} numberOfLines={1}>Tarjeta de contacto</Text>
        <Text color={detailColor} fontSize={10} numberOfLines={1} opacity={0.85}>{saved ? 'En tu agenda' : 'Toca para ver y guardar'}</Text>
      </View>
      <ChevronRight color={detailColor} size={16} />
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
    minWidth: 200,
    overflow: 'hidden',
    paddingHorizontal: 10,
    paddingVertical: 9,
  },
  body: { flex: 1, gap: 1, minWidth: 110 },
});
