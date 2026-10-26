import { StyleSheet } from 'react-native';
import { ChevronDown, ChevronUp, Search, X } from '@tamagui/lucide-icons-2';
import { Input, Text, XStack } from 'tamagui';

import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { describeSearchPosition } from '@/domain/chat-search';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

type ChatSearchBarProps = {
  index: number;
  matches: number;
  onChangeQuery: (value: string) => void;
  onClose: () => void;
  onNext: () => void;
  onPrevious: () => void;
  query: string;
};

function BarAction({ disabled = false, label, onPress, children }: { disabled?: boolean; label: string; onPress: () => void; children: React.ReactNode }) {
  return (
    <NativeRipplePressable accessibilityLabel={label} accessibilityRole="button" disabled={disabled} onPress={onPress} style={styles.action}>
      {children}
    </NativeRipplePressable>
  );
}

export function ChatSearchBar({ index, matches, onChangeQuery, onClose, onNext, onPrevious, query }: ChatSearchBarProps) {
  const theme = useTheme();
  const scale = useTextScale();
  const empty = matches === 0;
  return (
    <XStack alignItems="center" backgroundColor={theme.backgroundElement} borderTopColor={theme.border} borderTopWidth={StyleSheet.hairlineWidth} gap={2} paddingHorizontal={4} paddingVertical={8}>
      <BarAction label="Cerrar búsqueda" onPress={onClose}><X color={theme.text} size={22} /></BarAction>
      <XStack alignItems="center" backgroundColor={theme.backgroundSelected} borderRadius={25} flex={1} gap={8} paddingLeft={12} style={styles.field}>
        <Search color={theme.placeholder} size={18} />
        <Input
          accessibilityLabel="Buscar en la conversación"
          autoFocus
          backgroundColor="transparent"
          borderWidth={0}
          color={theme.text}
          flex={1}
          fontSize={scale(15)}
          height={50}
          onChangeText={onChangeQuery}
          placeholder="Buscar en la conversación"
          placeholderTextColor={theme.placeholder}
          style={styles.input}
          value={query}
        />
      </XStack>
      <Text color={empty ? theme.placeholder : theme.textSecondary} fontSize={scale(12)} minWidth={52} paddingHorizontal={6} textAlign="center">
        {describeSearchPosition(index, matches)}
      </Text>
      <BarAction disabled={empty} label="Resultado anterior" onPress={onPrevious}>
        <ChevronUp color={empty ? theme.placeholder : theme.text} size={22} />
      </BarAction>
      <BarAction disabled={empty} label="Resultado siguiente" onPress={onNext}>
        <ChevronDown color={empty ? theme.placeholder : theme.text} size={22} />
      </BarAction>
    </XStack>
  );
}

const styles = StyleSheet.create({
  action: { alignItems: 'center', borderRadius: 22, height: 44, justifyContent: 'center', overflow: 'hidden', width: 36 },
  field: { minHeight: 50 },
  input: { paddingHorizontal: 0, paddingVertical: 0 },
});
