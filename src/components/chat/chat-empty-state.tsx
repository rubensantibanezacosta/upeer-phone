import { StyleSheet, View } from 'react-native';
import { ShieldCheck } from '@tamagui/lucide-icons-2';
import { Paragraph, SizableText, YStack } from 'tamagui';

import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

export function ChatEmptyState() {
  const theme = useTheme();
  const scale = useTextScale();
  return (
    <YStack alignItems="center" flex={1} gap={12} justifyContent="center" padding={24}>
      <View style={[styles.icon, { backgroundColor: theme.backgroundElement }]}>
        <ShieldCheck color={theme.placeholder} size={28} />
      </View>
      <SizableText color={theme.text} fontSize={scale(17)} fontWeight="600" textAlign="center">Conversación segura activada</SizableText>
      <Paragraph color={theme.placeholder} textAlign="center">
        Aún no hay mensajes aquí. Envía el primero para comenzar esta conversación cifrada de extremo a extremo.
      </Paragraph>
    </YStack>
  );
}

const styles = StyleSheet.create({
  icon: { alignItems: 'center', borderRadius: 32, height: 64, justifyContent: 'center', width: 64 },
});
