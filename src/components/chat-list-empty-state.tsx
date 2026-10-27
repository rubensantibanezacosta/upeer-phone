import { StyleSheet, View } from 'react-native';
import { MessageCircle } from '@tamagui/lucide-icons-2';
import { Paragraph, SizableText, YStack } from 'tamagui';

import { NativeRippleButton } from '@/components/native-ripple-button';
import type { ChatListFilter } from '@/domain/chat-list-filters';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

type ChatListEmptyStateProps = {
  filter: ChatListFilter;
  filtering: boolean;
  onNewConversation: () => void;
};

export function ChatListEmptyState({ filter, filtering, onNewConversation }: ChatListEmptyStateProps) {
  const theme = useTheme();
  const scale = useTextScale();

  return <YStack alignItems="center" flex={1} gap={12} justifyContent="center" padding={24}>
    <View style={[styles.emptyAvatar, { backgroundColor: theme.backgroundElement }]}>
      <MessageCircle color={theme.placeholder} size={28} />
    </View>
    {filter === 'groups' ? <>
      <SizableText color={theme.text} fontSize={scale(17)} fontWeight="600">Sin grupos</SizableText>
      <Paragraph color={theme.placeholder} textAlign="center">Los grupos que crees aparecerán aquí.</Paragraph>
    </> : filtering ? <>
      <SizableText color={theme.text} fontSize={scale(17)} fontWeight="600">Sin coincidencias</SizableText>
      <Paragraph color={theme.placeholder} textAlign="center">Ninguna conversación coincide con la búsqueda o el filtro activo.</Paragraph>
    </> : <>
      <SizableText color={theme.text} fontSize={scale(17)} fontWeight="600">Tus conversaciones</SizableText>
      <Paragraph color={theme.placeholder} textAlign="center">Añade un contacto para empezar. Tus mensajes y conversaciones se mostrarán en esta lista.</Paragraph>
      <NativeRippleButton onPress={onNewConversation}>Nueva conversación</NativeRippleButton>
    </>}
  </YStack>;
}

const styles = StyleSheet.create({ emptyAvatar: { alignItems: 'center', borderRadius: 32, height: 64, justifyContent: 'center', width: 64 } });
