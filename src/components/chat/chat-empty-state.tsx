import { StyleSheet, View } from 'react-native';
import { ShieldCheck } from '@tamagui/lucide-icons-2';
import { SizableText, Text, YStack } from 'tamagui';

import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

export function ChatEmptyState() {
  const theme = useTheme();
  const scale = useTextScale();
  return (
    <YStack alignItems="center" flex={1} justifyContent="center" opacity={0.8} padding={32}>
      <View style={[styles.icon, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
        <ShieldCheck color={theme.accent} size={48} strokeWidth={1.8} />
      </View>
      <SizableText color={theme.text} fontSize={scale(18)} fontWeight="600" marginBottom={8} textAlign="center">Conversación Segura Activada</SizableText>
      <Text color={theme.textSecondary} fontSize={scale(14)} lineHeight={scale(22)} maxWidth={320} textAlign="center">
        Aún no hay mensajes aquí. Envía el primero para comenzar esta conversación cifrada de extremo a extremo.
      </Text>
    </YStack>
  );
}

const styles = StyleSheet.create({
  icon: { alignItems: 'center', borderRadius: 16, borderWidth: StyleSheet.hairlineWidth, height: 100, justifyContent: 'center', marginBottom: 24, width: 100 },
});
