import { useCallback, useEffect, useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { Fingerprint, LockKeyhole } from '@tamagui/lucide-icons-2';
import { SizableText, Text, YStack } from 'tamagui';

import { NativeRippleButton } from '@/components/native-ripple-button';
import { biometricKindLabel, biometricPromptTitle, type BiometricKind } from '@/domain/biometric-lock';
import { askForBiometrics } from '@/services/biometric-lock';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

export function BiometricLockScreen({ kind, onUnlocked }: { kind: BiometricKind; onUnlocked: () => void }) {
  const theme = useTheme();
  const scale = useTextScale();
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const ask = useCallback(async () => {
    setBusy(true);
    const result = await askForBiometrics(kind);
    setBusy(false);
    setError(result.message);
    if (result.success) onUnlocked();
  }, [kind, onUnlocked]);

  useEffect(() => {
    const timer = setTimeout(() => void ask(), 0);
    return () => clearTimeout(timer);
  }, [ask]);

  return <View style={[styles.root, { backgroundColor: theme.background }]}>
    <YStack alignItems="center" flex={1} gap={16} justifyContent="center" paddingHorizontal={32}>
      <View style={[styles.badge, { backgroundColor: theme.accentSoft }]}>
        <LockKeyhole color={theme.onAccentSoft} size={26} />
      </View>
      <YStack alignItems="center" gap={6}>
        <SizableText color={theme.text} fontSize={scale(19)} fontWeight="700" textAlign="center">uPeer bloqueado</SizableText>
        <Text color={theme.placeholder} fontSize={scale(13)} textAlign="center">
          {biometricPromptTitle(kind)}
        </Text>
      </YStack>
      <Text color={theme.danger} fontSize={scale(12)} minHeight={20} textAlign="center">{error}</Text>
      <NativeRippleButton
        accessibilityLabel={`Desbloquear con ${biometricKindLabel(kind).toLowerCase()}`}
        backgroundColor={theme.accent}
        color={theme.onAccent}
        disabled={busy}
        fontWeight="700"
        height={48}
        icon={<Fingerprint color={theme.onAccent} size={19} />}
        onPress={() => void ask()}
        width={220}
      >
        {busy ? 'Comprobando…' : 'Desbloquear'}
      </NativeRippleButton>
    </YStack>
  </View>;
}

const styles = StyleSheet.create({
  badge: { alignItems: 'center', borderRadius: 26, height: 52, justifyContent: 'center', width: 52 },
  root: { bottom: 0, left: 0, position: 'absolute', right: 0, top: 0, zIndex: 9000 },
});
