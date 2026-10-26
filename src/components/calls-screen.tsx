import { ScrollView, StyleSheet, View } from 'react-native';
import { router } from 'expo-router';
import { Clock3, Phone, PhoneMissed, Video } from '@tamagui/lucide-icons-2';
import { H1, Paragraph, SizableText, Text, XStack, YStack } from 'tamagui';

import { recentCallKindLabel, recentCallMissed, recentCallResultLabel } from '@/domain/call-history';
import type { CallHistoryItem } from '@/hooks/use-recent-calls';
import { useRecentCalls } from '@/hooks/use-recent-calls';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';
import { LoadingState } from '@/components/loading-state';
import { ProfileAvatar } from '@/components/profile-avatar';
import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { SettingsGroup, SettingsGroupLabel } from '@/components/settings-section-parts';

export function CallsScreen() {
  const theme = useTheme();
  const scale = useTextScale();
  const { calls, loading } = useRecentCalls();

  return (
    <YStack backgroundColor={theme.background} flex={1}>
      <ScrollView contentContainerStyle={styles.content}>
        <YStack gap={2} paddingHorizontal={16}>
          <H1 color={theme.text} fontSize={scale(24)} fontWeight="700">Llamadas</H1>
          <Text color={theme.placeholder} fontSize={scale(12)}>Las llamadas de voz y vídeo aparecen aquí cuando terminan.</Text>
        </YStack>
        {loading && calls.length === 0 ?
          <LoadingState compact label="Leyendo llamadas…" /> :
          calls.length === 0 ? <EmptyCalls /> : <YStack gap={10} paddingHorizontal={16}>
            <SettingsGroupLabel title="Recientes">{calls.length === 1 ? '1 llamada' : `${calls.length} llamadas`}</SettingsGroupLabel>
            <SettingsGroup>
              {calls.map((call) => <CallRow call={call} key={call.id} />)}
            </SettingsGroup>
          </YStack>}
      </ScrollView>
    </YStack>
  );
}

function EmptyCalls() {
  const theme = useTheme();
  const scale = useTextScale();
  return (
    <YStack alignItems="center" flex={1} gap={12} justifyContent="center" padding={24}>
      <YStack alignItems="center" backgroundColor={theme.backgroundElement} borderRadius={32} height={64} justifyContent="center" width={64}>
        <Clock3 color={theme.placeholder} size={28} />
      </YStack>
      <SizableText color={theme.text} fontSize={scale(17)} fontWeight="600">Todavía no hay llamadas</SizableText>
      <Paragraph color={theme.placeholder} textAlign="center">Cuando una llamada termine verás aquí quién llamó, cuánto duró y si se perdió.</Paragraph>
    </YStack>
  );
}

function CallRow({ call }: { call: CallHistoryItem }) {
  const theme = useTheme();
  const scale = useTextScale();
  const missed = recentCallMissed(call);
  const KindIcon = call.kind === 'video' ? Video : missed ? PhoneMissed : Phone;
  const KindColor = missed ? theme.danger : theme.accent;
  return (
    <NativeRipplePressable accessibilityLabel={`Abrir la conversación de ${call.title}`} onPress={() => router.push({ pathname: '/chat/[upeerId]', params: { upeerId: call.chatId } })}>
      <XStack alignItems="center" gap={12} paddingHorizontal={14} paddingVertical={10}>
        <View style={styles.avatar}>
          <ProfileAvatar avatar={call.avatar} name={call.title} size={44} />
          <View style={[styles.kindBadge, { backgroundColor: theme.backgroundElement, borderColor: theme.background }]}>
            <KindIcon color={KindColor} size={12} />
          </View>
        </View>
        <YStack flex={1} gap={2} minWidth={0}>
          <SizableText color={missed ? theme.danger : theme.text} fontSize={scale(14)} fontWeight="600" numberOfLines={1}>{call.title}</SizableText>
          <XStack alignItems="center" gap={6}>
            <Text color={theme.placeholder} fontSize={scale(12)} numberOfLines={1}>{call.group ? 'Grupo · ' : ''}{recentCallKindLabel(call)}</Text>
            <Text color={theme.placeholder} fontSize={scale(12)}>·</Text>
            <Text color={call.duration ? theme.textSecondary : KindColor} fontSize={scale(12)} fontWeight="600">{recentCallResultLabel(call)}</Text>
          </XStack>
        </YStack>
        <Text color={theme.placeholder} fontSize={scale(11)}>{describeCallTime(call.timestamp)}</Text>
      </XStack>
    </NativeRipplePressable>
  );
}

function describeCallTime(timestamp: number): string {
  if (!Number.isFinite(timestamp) || timestamp <= 0) return '';
  const date = new Date(timestamp);
  const now = new Date();
  if (date.toDateString() === now.toDateString()) return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  return date.toLocaleDateString([], { day: 'numeric', month: 'short' });
}

const styles = StyleSheet.create({
  avatar: { height: 44, position: 'relative', width: 44 },
  content: { gap: 18, paddingBottom: 24, paddingTop: 12 },
  kindBadge: { alignItems: 'center', borderRadius: 9, borderWidth: 2, bottom: -3, height: 20, justifyContent: 'center', position: 'absolute', right: -3, width: 20 },
});
