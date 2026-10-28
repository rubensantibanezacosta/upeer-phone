import { Modal, Pressable, StyleSheet, View } from 'react-native';
import { Phone, PhoneOff } from '@tamagui/lucide-icons-2';
import { SizableText, Text, XStack, YStack } from 'tamagui';

import { ProfileAvatar } from '@/components/profile-avatar';
import { callKindLabel, type CallView } from '@/domain/call-session';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

type IncomingCallSheetProps = {
  call: CallView;
  peerName: string;
  avatar?: string;
  onAccept: () => void;
  onReject: () => void;
};

export function IncomingCallSheet({ call, peerName, avatar, onAccept, onReject }: IncomingCallSheetProps) {
  const theme = useTheme();
  const scale = useTextScale();

  return (
    <Modal animationType="fade" onRequestClose={onReject} presentationStyle="fullScreen" statusBarTranslucent transparent visible>
      <View style={[styles.root, { backgroundColor: theme.background }]}>
        <YStack alignItems="center" flex={1} gap={10} justifyContent="center" paddingHorizontal={24}>
          <ProfileAvatar avatar={avatar} name={peerName} size={104} />
          <SizableText color={theme.text} fontSize={scale(22)} fontWeight="700" marginTop={14} numberOfLines={1}>{peerName}</SizableText>
          <Text color={theme.placeholder} fontSize={scale(14)}>{callKindLabel(call)} entrante</Text>
        </YStack>
        <XStack gap={64} justifyContent="center" paddingBottom={56}>
          <CallAction color={theme.danger} label="Rechazar" onPress={onReject}>
            <PhoneOff color={theme.onAccent} size={28} />
          </CallAction>
          <CallAction color={theme.success} label="Aceptar" onPress={onAccept}>
            <Phone color={theme.onAccent} size={28} />
          </CallAction>
        </XStack>
      </View>
    </Modal>
  );
}

function CallAction({ color, label, onPress, children }: { color: string; label: string; onPress: () => void; children: React.ReactNode }) {
  const scale = useTextScale();
  const theme = useTheme();
  return (
    <YStack alignItems="center" gap={8}>
      <Pressable accessibilityLabel={label} accessibilityRole="button" onPress={onPress} style={[styles.action, { backgroundColor: color }]}>{children}</Pressable>
      <Text color={theme.placeholder} fontSize={scale(12)}>{label}</Text>
    </YStack>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, justifyContent: 'space-between' },
  action: { alignItems: 'center', borderRadius: 32, elevation: 6, height: 64, justifyContent: 'center', width: 64 },
});
