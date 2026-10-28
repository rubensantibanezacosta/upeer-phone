import { Modal, Pressable, StyleSheet, View } from 'react-native';
import { Mic, MicOff, PhoneOff, Video, VideoOff } from '@tamagui/lucide-icons-2';
import { SizableText, Text, XStack, YStack } from 'tamagui';

import { ProfileAvatar } from '@/components/profile-avatar';
import {
  CALL_AUDIO_NOTICE,
  callDurationLabel,
  callKindLabel,
  callPhaseLabel,
  canToggleMedia,
  isCallOver,
  type CallView,
} from '@/domain/call-session';
import { useNow } from '@/hooks/use-now';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

type ActiveCallOverlayProps = {
  call: CallView;
  peerName: string;
  avatar?: string;
  onHangUp: () => void;
  onToggleMute: () => void;
  onToggleCamera: () => void;
};

export function ActiveCallOverlay({ call, peerName, avatar, onHangUp, onToggleMute, onToggleCamera }: ActiveCallOverlayProps) {
  const theme = useTheme();
  const scale = useTextScale();
  const now = useNow(1000);
  const over = isCallOver(call);
  const toggleable = canToggleMedia(call);
  const onCall = call.phase === 'connected';

  return (
    <Modal animationType="fade" onRequestClose={onHangUp} presentationStyle="fullScreen" statusBarTranslucent transparent visible>
      <View style={[styles.root, { backgroundColor: theme.background }]}>
        <YStack alignItems="center" flex={1} gap={8} justifyContent="center" paddingHorizontal={24}>
          <ProfileAvatar avatar={avatar} name={peerName} size={104} />
          <SizableText color={theme.text} fontSize={scale(22)} fontWeight="700" marginTop={14} numberOfLines={1}>{peerName}</SizableText>
          <Text color={over ? theme.danger : theme.placeholder} fontSize={scale(14)}>{over ? callPhaseLabel(call) : `${callKindLabel(call)} · ${callPhaseLabel(call)}`}</Text>
          {onCall || over ? <Text color={theme.text} fontSize={scale(15)} fontWeight="600" marginTop={4}>{callDurationLabel(call, now)}</Text> : null}
          {toggleable ? <Text color={theme.placeholder} fontSize={scale(12)} marginTop={10} textAlign="center">{CALL_AUDIO_NOTICE}</Text> : null}
        </YStack>
        <XStack gap={28} justifyContent="center" paddingBottom={56}>
          {toggleable ? (
            <CallAction active={call.muted} label={call.muted ? 'Activar micro' : 'Silenciar'} onPress={onToggleMute}>
              {call.muted ? <MicOff color={theme.onAccent} size={24} /> : <Mic color={theme.onAccent} size={24} />}
            </CallAction>
          ) : null}
          {toggleable && call.kind === 'video' ? (
            <CallAction active={!call.cameraEnabled} label={call.cameraEnabled ? 'Apagar cámara' : 'Encender cámara'} onPress={onToggleCamera}>
              {call.cameraEnabled ? <Video color={theme.onAccent} size={24} /> : <VideoOff color={theme.onAccent} size={24} />}
            </CallAction>
          ) : null}
          <CallAction danger label={over ? 'Cerrar' : 'Colgar'} onPress={onHangUp}>
            <PhoneOff color={theme.onAccent} size={26} />
          </CallAction>
        </XStack>
      </View>
    </Modal>
  );
}

function CallAction({ active = false, danger = false, label, onPress, children }: {
  active?: boolean;
  danger?: boolean;
  label: string;
  onPress: () => void;
  children: React.ReactNode;
}) {
  const theme = useTheme();
  const scale = useTextScale();
  const background = danger ? theme.danger : active ? theme.accent : theme.backgroundElement;
  return (
    <YStack alignItems="center" gap={8}>
      <Pressable accessibilityLabel={label} accessibilityRole="button" onPress={onPress} style={[styles.action, { backgroundColor: background }]}>{children}</Pressable>
      <Text color={theme.placeholder} fontSize={scale(11)} numberOfLines={1}>{label}</Text>
    </YStack>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, justifyContent: 'space-between' },
  action: { alignItems: 'center', borderRadius: 28, height: 56, justifyContent: 'center', width: 56 },
});
