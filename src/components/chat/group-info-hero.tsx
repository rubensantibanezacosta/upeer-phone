import { StyleSheet, View } from 'react-native';
import { Phone, Search, UserPlus, Users, Video } from '@tamagui/lucide-icons-2';
import { SizableText, Text, XStack, YStack } from 'tamagui';

import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { InteractiveRow } from '@/components/contact-info/interactive-row';
import { ProfileAvatar } from '@/components/profile-avatar';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

type GroupInfoHeroProps = {
  avatar?: string;
  canAdd: boolean;
  canRename: boolean;
  invited: boolean;
  memberCount: number;
  name: string;
  onAdd: () => void;
  onCall: () => void;
  onRename: () => void;
  onSearch: () => void;
  onVideo: () => void;
};

export function GroupInfoHero({ avatar, canAdd, canRename, invited, memberCount, name, onAdd, onCall, onRename, onSearch, onVideo }: GroupInfoHeroProps) {
  const theme = useTheme();
  const scale = useTextScale();

  return <YStack alignItems="center" gap={5} paddingTop={4}>
    {avatar ? <ProfileAvatar avatar={avatar} name={name} size={96} /> : <View style={[styles.groupAvatar, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}><Users color={theme.placeholder} size={40} /></View>}
    <NativeRipplePressable accessibilityLabel="Renombrar grupo" accessibilityRole="button" disabled={!canRename} onPress={onRename}>
      <SizableText color={theme.text} fontSize={scale(20)} fontWeight="700" numberOfLines={2} textAlign="center">{name}</SizableText>
    </NativeRipplePressable>
    <XStack alignItems="center" gap={5}>
      <Text color={theme.placeholder} fontSize={scale(12)}>{`Grupo · ${memberCount} ${memberCount === 1 ? 'miembro' : 'miembros'}`}</Text>
    </XStack>
    {invited ? <Text color={theme.accent} fontSize={scale(12)}>Invitación pendiente</Text> : null}
    <XStack gap={18} paddingTop={12}>
      <InteractiveRow accessibilityLabel="Llamar al grupo" disabled icon={Phone} label="Llamar" onPress={onCall} variant="action" />
      <InteractiveRow accessibilityLabel="Videollamar al grupo" disabled icon={Video} label="Vídeo" onPress={onVideo} variant="action" />
      <InteractiveRow accessibilityLabel="Buscar en el chat" icon={Search} label="Buscar" onPress={onSearch} variant="action" />
      <InteractiveRow accessibilityLabel="Añadir miembros" disabled={!canAdd} icon={UserPlus} label="Añadir" onPress={onAdd} variant="action" />
    </XStack>
  </YStack>;
}

const styles = StyleSheet.create({
  groupAvatar: { alignItems: 'center', borderRadius: 48, borderWidth: StyleSheet.hairlineWidth, height: 96, justifyContent: 'center', width: 96 },
});
