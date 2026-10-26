import { useState } from 'react';
import { Alert, StyleSheet, View } from 'react-native';
import { Check, Copy, KeyRound, ShieldAlert } from '@tamagui/lucide-icons-2';
import { Paragraph, SizableText, Text, XStack, YStack } from 'tamagui';

import type { UPeerIdentity } from '@/domain/identity-record';
import { describeRecoveryPhrase } from '@/domain/recovery-phrase';
import { BottomSheet } from '@/components/bottom-sheet';
import { NativeRippleButton } from '@/components/native-ripple-button';
import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { SettingsGroup, SettingsGroupLabel, SettingsNote, SettingsSectionHeader } from '@/components/settings-section-parts';
import { useCopyToClipboard } from '@/hooks/use-copy-to-clipboard';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

export function SecuritySettingsSection({ identity }: { identity: UPeerIdentity }) {
  const theme = useTheme();
  const scale = useTextScale();
  const phrase = describeRecoveryPhrase(identity);
  const [sheetOpen, setSheetOpen] = useState(false);

  const openPhrase = () => {
    Alert.alert(
      'Ver la frase de recuperación',
      'Son las doce palabras que abren tu cuenta en otro dispositivo. No las compartas ni las hagas capturas: quien las tenga puede entrar como tú.',
      [{ text: 'Cancelar', style: 'cancel' }, { text: 'Ver la frase', onPress: () => setSheetOpen(true) }],
    );
  };

  return <YStack gap={12}>
    <SettingsSectionHeader icon={<KeyRound color={theme.accent} size={18} />} title="Seguridad">Tu cuenta se recupera con la frase de doce palabras y nada más. No hay servidor que pueda devolvértela.</SettingsSectionHeader>

    <SettingsGroupLabel title="Frase de recuperación">{phrase.available ? 'Guárdala fuera del teléfono' : 'No disponible en este dispositivo'}</SettingsGroupLabel>
    <SettingsGroup>
      <NativeRipplePressable
        accessibilityLabel="Ver la frase de recuperación"
        disabled={!phrase.available}
        onPress={openPhrase}
        style={[styles.row, { opacity: phrase.available ? 1 : 0.5 }]}
      >
        <YStack flex={1} gap={2}>
          <SizableText color={theme.text} fontSize={scale(15)} fontWeight="500">Ver la frase de recuperación</SizableText>
          <Text color={theme.placeholder} fontSize={scale(12)}>{phrase.available ? 'Doce palabras, una sola vez' : phrase.reason}</Text>
        </YStack>
        <KeyRound color={theme.placeholder} size={18} />
      </NativeRipplePressable>
    </SettingsGroup>

    <SettingsNote>Si pierdes el teléfono y no tienes la frase, la cuenta y sus mensajes no se pueden recuperar. La frase solo se muestra aquí, nunca se envía a ningún servidor.</SettingsNote>

    <YStack alignItems="center" backgroundColor={theme.dangerSoft} borderColor={theme.dangerBorder} borderRadius={12} borderWidth={1} flexDirection="row" gap={10} padding={12}>
      <ShieldAlert color={theme.onDangerSoft} size={16} />
      <Paragraph color={theme.onDangerSoft} flex={1} fontSize={scale(12)}>Nadie de soporte te pedirá la frase. Si alguien lo hace, es un fraude.</Paragraph>
    </YStack>

    <RecoveryPhraseSheet onClose={() => setSheetOpen(false)} phrase={phrase.words} visible={sheetOpen} />
  </YStack>;
}

function RecoveryPhraseSheet({ onClose, phrase, visible }: { onClose: () => void; phrase: string[]; visible: boolean }) {
  const theme = useTheme();
  const scale = useTextScale();
  const copyToClipboard = useCopyToClipboard();
  return <BottomSheet icon={<KeyRound color={theme.accent} size={20} />} onClose={onClose} title="Frase de recuperación" visible={visible}>
    <Paragraph color={theme.placeholder} fontSize={scale(12)}>Escríbela en papel y guárdala en un sitio seguro. Es la única forma de abrir tu cuenta en otro dispositivo.</Paragraph>
    <View style={[styles.grid, { backgroundColor: theme.background, borderColor: theme.border }]}>
      {phrase.map((word, index) => <XStack alignItems="center" gap={6} key={`${index}-${word}`} style={styles.word}>
        <Text color={theme.placeholder} fontSize={scale(11)} fontWeight="600">{index + 1}</Text>
        <SizableText color={theme.text} fontFamily="$mono" fontSize={scale(13)} fontWeight="600">{word}</SizableText>
      </XStack>)}
    </View>
    <NativeRippleButton backgroundColor={theme.accentSoft} color={theme.onAccentSoft} height={46} icon={<Copy color={theme.onAccentSoft} size={17} />} onPress={() => void copyToClipboard(phrase.join(' '), 'Frase copiada. Bórrala del portapapeles al guardarla.')}>Copiar las doce palabras</NativeRippleButton>
    <NativeRippleButton backgroundColor="transparent" borderColor={theme.border} borderWidth={1} color={theme.text} height={44} icon={<Check color={theme.text} size={17} />} onPress={onClose}>Ya la tengo guardada</NativeRippleButton>
  </BottomSheet>;
}

const styles = StyleSheet.create({
  grid: { borderRadius: 12, borderWidth: StyleSheet.hairlineWidth, flexDirection: 'row', flexWrap: 'wrap', padding: 10 },
  row: { alignItems: 'center', flexDirection: 'row', gap: 12, minHeight: 64, paddingHorizontal: 16, paddingVertical: 12 },
  word: { paddingVertical: 4, width: '50%' },
});
