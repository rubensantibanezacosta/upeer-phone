import { useState } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { ImageManipulator, SaveFormat } from 'expo-image-manipulator';
import * as ImagePicker from 'expo-image-picker';
import { Check, ChevronLeft, Copy, ImagePlus, KeyRound, QrCode, RotateCcw, UserPlus } from '@tamagui/lucide-icons-2';
import { Card, H1, Input, Paragraph, Separator, SizableText, TextArea, XStack, YStack } from 'tamagui';

import { DevicePairingPanel } from '@/components/device-pairing-panel';
import { createMnemonic, deriveIdentity, type UPeerIdentity } from '@/domain/identity';
import { clearIdentity, loadIdentity, saveIdentity, saveProfile } from '@/storage/identity-store';
import { CopyField } from '@/components/copy-field';
import { NativeRippleButton } from '@/components/native-ripple-button';
import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { ProfileAvatar } from '@/components/profile-avatar';
import { useCopyToClipboard } from '@/hooks/use-copy-to-clipboard';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

type AuthScreenProps = { identityExists: boolean; onAuthenticated: (identity: UPeerIdentity) => void };

export function AuthScreen({ identityExists, onAuthenticated }: AuthScreenProps) {
  const [mnemonic, setMnemonic] = useState('');
  const [generatedPhrase, setGeneratedPhrase] = useState('');
  const [alias, setAlias] = useState('');
  const [avatar, setAvatar] = useState('');
  const [avatarBusy, setAvatarBusy] = useState(false);
  const [mode, setMode] = useState<'create' | 'recover' | 'unlock' | null>(null);
  const [pairing, setPairing] = useState(false);
  const [error, setError] = useState('');
  const theme = useTheme();
  const scale = useTextScale();
  const copyToClipboard = useCopyToClipboard();

  const runWithMnemonic = async (phrase: string, nextAlias: string, nextAvatar = '') => {
    setError('');
    try {
      const identity = await deriveIdentity(phrase);
      if (mode === 'unlock') {
        const stored = await loadIdentity();
        if (!stored || stored.upeerId !== identity.upeerId) {
          throw new Error('La frase no corresponde a la identidad guardada en este dispositivo.');
        }
      } else {
        const stored = await loadIdentity();
        if (stored && stored.upeerId !== identity.upeerId) {
          await clearIdentity();
        }
        await saveIdentity(identity);
        await saveProfile({ upeerId: identity.upeerId, publicKey: identity.publicKey, alias: nextAlias.trim().slice(0, 64), ...(nextAvatar ? { avatar: nextAvatar } : {}) });
      }
      onAuthenticated(identity);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'No se pudo preparar la identidad.');
    }
  };

  const chooseAvatar = async () => {
    setAvatarBusy(true);
    setError('');
    try {
      const result = await ImagePicker.launchImageLibraryAsync({ mediaTypes: ['images'], allowsEditing: true, aspect: [1, 1], quality: 0.9 });
      if (result.canceled || !result.assets[0]) return;
      const context = ImageManipulator.manipulate(result.assets[0].uri);
      context.resize({ width: 512, height: 512 });
      const rendered = await context.renderAsync();
      const compressed = await rendered.saveAsync({ format: SaveFormat.JPEG, compress: 0.78, base64: true });
      if (!compressed.base64) throw new Error('No se pudo procesar la foto seleccionada.');
      setAvatar(`data:image/jpeg;base64,${compressed.base64}`);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'No se pudo preparar la foto de perfil.');
    } finally {
      setAvatarBusy(false);
    }
  };

  const handleCreate = () => {
    setGeneratedPhrase(createMnemonic());
    setMnemonic('');
    setAvatar('');
    setMode('create');
  };

  const handleMode = (nextMode: 'recover' | 'unlock') => {
    setMnemonic('');
    setAvatar('');
    setPairing(false);
    setMode(nextMode);
    setError('');
  };

  const matchedPhrase = normalizeMnemonic(mnemonic) === generatedPhrase;

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: theme.screen }}>
      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : 'height'} style={{ flex: 1 }}>
        <ScrollView contentContainerStyle={{ flexGrow: 1, justifyContent: 'center', padding: 24, paddingBottom: 48 }} keyboardDismissMode={Platform.OS === 'ios' ? 'interactive' : 'on-drag'} keyboardShouldPersistTaps="handled">
          <YStack alignSelf="center" gap={24} width="100%" maxWidth={480}>
            <YStack gap={8} paddingHorizontal={4}>
          
              <H1 color="color" fontSize={scale(24)} fontWeight="700" letterSpacing={-1.5}>uPeer</H1>
              <Paragraph color="placeholder-color" fontSize={scale(16)}>Tu identidad. Tus contactos. Tu privacidad.</Paragraph>
            </YStack>
            <Card borderWidth={1} backgroundColor="color-1" borderColor="border-color" borderRadius={20} padding={24}>
              <YStack gap={20}>
                <YStack gap={8}>
                  <SizableText color="color" fontSize={scale(21)} fontWeight="700">{mode === 'create' ? 'Crea tu identidad' : mode === 'recover' ? 'Recupera tu identidad' : mode === 'unlock' ? 'Desbloquea tu identidad' : 'Empieza a conversar en privado'}</SizableText>
                  {!mode && <Paragraph color="placeholder-color">{identityExists ? 'Tu identidad está protegida. Introduce tu frase para desbloquearla o recuperar otra cuenta.' : 'Crea una identidad segura o recupera el acceso a la que ya tienes en otro dispositivo.'}</Paragraph>}
                </YStack>
                {generatedPhrase.length > 0 && (
                  <YStack backgroundColor="color-2" borderColor="border-color" borderRadius={14} borderWidth={1} gap={8} padding={16}>
                    <SizableText color="accent-color" fontSize={scale(14)} fontWeight="700">GUARDA TU FRASE DE RECUPERACIÓN</SizableText>
                    <CopyField accessibilityLabel="Copiar palabras" onCopy={() => void copyToClipboard(generatedPhrase, 'Palabras copiadas')} value={generatedPhrase} />
                    <Paragraph color="placeholder-color" fontSize={scale(13)}>No la compartas. La necesitarás para recuperar tu identidad.</Paragraph>
                    <NativeRippleButton backgroundColor={theme.accentSoft} color={theme.text} height={44} icon={<Copy color={theme.onAccentSoft} size={15} />} onPress={() => void copyToClipboard(generatedPhrase, 'Palabras copiadas')} width="100%">Copiar palabras</NativeRippleButton>
                  </YStack>
                )}
                {mode && <>
                  <Separator borderColor="border-color" />
                  {mode === 'recover' && pairing
                    ? <DevicePairingPanel />
                    : <YStack gap={12}>
                    <TextArea accessibilityLabel="Frase secreta de 12 palabras" autoCapitalize="none" autoCorrect={false} backgroundColor="color-2" borderColor="border-color" color="color" height={mode === 'create' ? 86 : 112} onChangeText={setMnemonic} placeholder={mode === 'create' ? 'Escribe las 12 palabras para confirmar' : 'Escribe las 12 palabras en orden'} placeholderTextColor="placeholder-color" textAlignVertical="top" value={mnemonic} />
                    {mode === 'create' && <YStack gap={10}>
                      <SizableText color="placeholder-color" fontSize={scale(10)} fontWeight="600" letterSpacing={0.7}>FOTO DE PERFIL (OPCIONAL)</SizableText>
                      <XStack alignItems="center" gap={14}>
                        <NativeRipplePressable accessibilityLabel={avatar ? 'Cambiar foto de perfil' : 'Añadir foto de perfil'} accessibilityRole="button" disabled={avatarBusy} onPress={() => void chooseAvatar()}>
                          <ProfileAvatar avatar={avatar || undefined} name={alias || '?'} size={72} />
                        </NativeRipplePressable>
                        <YStack alignItems="flex-start" gap={8}>
                          <NativeRippleButton backgroundColor={theme.accentSoft} color={theme.text} disabled={avatarBusy} height={38} icon={<ImagePlus color={theme.onAccentSoft} size={15} />} onPress={() => void chooseAvatar()}>{avatarBusy ? 'Procesando…' : avatar ? 'Cambiar foto' : 'Añadir foto'}</NativeRippleButton>
                          {avatar ? <SizableText accessibilityLabel="Quitar foto de perfil" color={theme.danger} fontSize={scale(12)} onPress={() => { setAvatar(''); setError(''); }} paddingHorizontal={4} role="button">Quitar foto</SizableText> : null}
                        </YStack>
                      </XStack>
                      <Input accessibilityLabel="Nombre visible" autoCapitalize="words" backgroundColor="color-2" borderColor="border-color" color="color" onChangeText={setAlias} placeholder="Tu nombre" placeholderTextColor="placeholder-color" value={alias} />
                    </YStack>}
                  </YStack>}
                </>}
                {!!error && <Paragraph accessibilityRole="alert" backgroundColor={theme.dangerSoft} borderColor={theme.dangerBorder} borderRadius={10} borderWidth={1} color={theme.onDangerSoft} padding={12}>{error}</Paragraph>}
                {!mode && <YStack gap={12}>
                  <NativeRippleButton backgroundColor={theme.accent} color={theme.onAccent} fontWeight="700" height={50} icon={<UserPlus color={theme.onAccent} size={18} />} onPress={handleCreate}>Crear identidad nueva</NativeRippleButton>
                  <NativeRippleButton backgroundColor={theme.accentSoft} color={theme.text} height={50} icon={<RotateCcw color={theme.onAccentSoft} size={18} />} onPress={() => handleMode(identityExists ? 'unlock' : 'recover')}>{identityExists ? 'Desbloquear con mi frase' : 'Recuperar identidad'}</NativeRippleButton>
                </YStack>}
                {mode && <YStack gap={12}>
                  {mode === 'recover' && pairing && <NativeRippleButton backgroundColor={theme.accentSoft} color={theme.text} height={44} icon={<KeyRound color={theme.onAccentSoft} size={17} />} onPress={() => setPairing(false)}>Usar la frase de recuperación</NativeRippleButton>}
                  {(mode === 'recover' || mode === 'unlock') && !pairing && <NativeRippleButton backgroundColor={theme.accent} color={theme.onAccent} fontWeight="700" height={50} icon={mode === 'recover' ? <RotateCcw color={theme.onAccent} size={18} /> : <KeyRound color={theme.onAccent} size={18} />} onPress={() => void runWithMnemonic(mnemonic, '')}>{mode === 'recover' ? 'Recuperar identidad' : 'Desbloquear identidad'}</NativeRippleButton>}
                  {mode === 'recover' && !pairing && <XStack alignItems="center" gap={8} justifyContent="center">
                    <QrCode color={theme.onAccentSoft} size={15} />
                    <SizableText accessibilityRole="button" color="placeholder-color" fontSize={scale(13)} onPress={() => { setError(''); setMnemonic(''); setPairing(true); }} padding={6}>No tengo la frase: vincular este dispositivo</SizableText>
                  </XStack>}
                  {mode === 'create' && matchedPhrase && <NativeRippleButton backgroundColor={theme.accent} color={theme.onAccent} fontWeight="700" height={50} icon={<Check color={theme.onAccent} size={18} />} onPress={() => void runWithMnemonic(generatedPhrase, alias, avatar)}>Confirmar frase y continuar</NativeRippleButton>}
                  <XStack alignItems="center" gap={6} justifyContent="center"><ChevronLeft color={theme.placeholder} size={15} /><SizableText color="placeholder-color" onPress={() => { setMode(null); setPairing(false); setGeneratedPhrase(''); setMnemonic(''); setAvatar(''); setError(''); }} padding={12} role="button">Volver</SizableText></XStack>
                </YStack>}
              </YStack>
            </Card>
          </YStack>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

function normalizeMnemonic(value: string): string {
  return value.normalize('NFKD').trim().toLowerCase().split(/\s+/).join(' ');
}