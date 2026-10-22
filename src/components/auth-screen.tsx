import { useState } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { ImageManipulator, SaveFormat } from 'expo-image-manipulator';
import * as ImagePicker from 'expo-image-picker';
import { Copy, ImagePlus } from '@tamagui/lucide-icons-2';
import { Card, H1, Input, Paragraph, Separator, SizableText, TextArea, XStack, YStack } from 'tamagui';

import { createMnemonic, deriveIdentity, type UPeerIdentity } from '@/domain/identity';
import { clearIdentity, loadIdentity, saveIdentity, saveProfile } from '@/storage/identity-store';
import { CopyField } from '@/components/copy-field';
import { NativeRippleButton } from '@/components/native-ripple-button';
import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { ProfileAvatar } from '@/components/profile-avatar';
import { useCopyToClipboard } from '@/hooks/use-copy-to-clipboard';
import { useTheme } from '@/hooks/use-theme';

type AuthScreenProps = { identityExists: boolean; onAuthenticated: (identity: UPeerIdentity) => void };

export function AuthScreen({ identityExists, onAuthenticated }: AuthScreenProps) {
  const [mnemonic, setMnemonic] = useState('');
  const [generatedPhrase, setGeneratedPhrase] = useState('');
  const [alias, setAlias] = useState('');
  const [avatar, setAvatar] = useState('');
  const [avatarBusy, setAvatarBusy] = useState(false);
  const [mode, setMode] = useState<'create' | 'recover' | 'unlock' | null>(null);
  const [error, setError] = useState('');
  const theme = useTheme();
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
    setMode(nextMode);
    setError('');
  };

  const matchedPhrase = normalizeMnemonic(mnemonic) === generatedPhrase;

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: '#202124' }}>
      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : 'height'} style={{ flex: 1 }}>
        <ScrollView contentContainerStyle={{ flexGrow: 1, justifyContent: 'center', padding: 24, paddingBottom: 48 }} keyboardDismissMode={Platform.OS === 'ios' ? 'interactive' : 'on-drag'} keyboardShouldPersistTaps="handled">
          <YStack alignSelf="center" gap={24} width="100%" maxWidth={480}>
            <YStack gap={8} paddingHorizontal={4}>
          
              <H1 color="color" fontSize={24} fontWeight="700" letterSpacing={-1.5}>uPeer</H1>
              <Paragraph color="placeholder-color" fontSize={16} lineHeight={24}>Tu identidad. Tus contactos. Tu privacidad.</Paragraph>
            </YStack>
            <Card borderWidth={1} backgroundColor="color-1" borderColor="border-color" borderRadius={20} padding={24}>
              <YStack gap={20}>
                <YStack gap={8}>
                  <SizableText color="color" fontSize={21} fontWeight="700">{mode === 'create' ? 'Crea tu identidad' : mode === 'recover' ? 'Recupera tu identidad' : mode === 'unlock' ? 'Desbloquea tu identidad' : 'Empieza a conversar en privado'}</SizableText>
                  {!mode && <Paragraph color="placeholder-color" lineHeight={24}>{identityExists ? 'Tu identidad está protegida. Introduce tu frase para desbloquearla o recuperar otra cuenta.' : 'Crea una identidad segura o recupérala con tu frase de 12 palabras.'}</Paragraph>}
                </YStack>
                {generatedPhrase.length > 0 && (
                  <YStack backgroundColor="color-2" borderColor="border-color" borderRadius={14} borderWidth={1} gap={8} padding={16}>
                    <SizableText color="accent-color" fontSize={14} fontWeight="700">GUARDA TU FRASE DE RECUPERACIÓN</SizableText>
                    <CopyField accessibilityLabel="Copiar palabras" onCopy={() => void copyToClipboard(generatedPhrase, 'Palabras copiadas')} value={generatedPhrase} />
                    <Paragraph color="placeholder-color" fontSize={13}>No la compartas. La necesitarás para recuperar tu identidad.</Paragraph>
                    <NativeRippleButton backgroundColor="transparent" borderColor="border-color" borderWidth={1} color="color" height={44} icon={<Copy color={theme.placeholder} size={15} />} onPress={() => void copyToClipboard(generatedPhrase, 'Palabras copiadas')} width="100%">Copiar palabras</NativeRippleButton>
                  </YStack>
                )}
                {mode && <>
                  <Separator borderColor="border-color" />
                  <YStack gap={12}>
                    <TextArea accessibilityLabel="Frase secreta de 12 palabras" autoCapitalize="none" autoCorrect={false} backgroundColor="color-2" borderColor="border-color" color="color" height={mode === 'create' ? 86 : 112} onChangeText={setMnemonic} placeholder={mode === 'create' ? 'Escribe las 12 palabras para confirmar' : 'Escribe las 12 palabras en orden'} placeholderTextColor="placeholder-color" textAlignVertical="top" value={mnemonic} />
                    {mode === 'create' && <YStack gap={10}>
                      <SizableText color="placeholder-color" fontSize={10} fontWeight="600" letterSpacing={0.7}>FOTO DE PERFIL (OPCIONAL)</SizableText>
                      <XStack alignItems="center" gap={14}>
                        <NativeRipplePressable accessibilityLabel={avatar ? 'Cambiar foto de perfil' : 'Añadir foto de perfil'} accessibilityRole="button" disabled={avatarBusy} onPress={() => void chooseAvatar()}>
                          <ProfileAvatar avatar={avatar || undefined} name={alias || '?'} size={72} />
                        </NativeRipplePressable>
                        <YStack alignItems="flex-start" gap={8}>
                          <NativeRippleButton backgroundColor="color-2" borderColor="border-color" borderWidth={1} color="color" disabled={avatarBusy} height={38} icon={<ImagePlus color={theme.placeholder} size={15} />} onPress={() => void chooseAvatar()}>{avatarBusy ? 'Procesando…' : avatar ? 'Cambiar foto' : 'Añadir foto'}</NativeRippleButton>
                          {avatar ? <SizableText accessibilityLabel="Quitar foto de perfil" color="#fb7185" fontSize={12} onPress={() => { setAvatar(''); setError(''); }} paddingHorizontal={4} role="button">Quitar foto</SizableText> : null}
                        </YStack>
                      </XStack>
                      <Input accessibilityLabel="Nombre visible" autoCapitalize="words" backgroundColor="color-2" borderColor="border-color" color="color" onChangeText={setAlias} placeholder="Tu nombre" placeholderTextColor="placeholder-color" value={alias} />
                    </YStack>}
                  </YStack>
                </>}
                {!!error && <Paragraph accessibilityRole="alert" backgroundColor="#3b1118" borderColor="#fb7185" borderRadius={10} borderWidth={1} color="#ffe4e6" padding={12}>{error}</Paragraph>}
                {!mode && <YStack gap={12}>
                  <NativeRippleButton backgroundColor={theme.accent} color={theme.onAccent} fontWeight="700" height={50} onPress={handleCreate}>Crear identidad nueva</NativeRippleButton>
                  <NativeRippleButton backgroundColor="color-2" borderColor="border-color" borderWidth={1} color="color" height={50} onPress={() => handleMode(identityExists ? 'unlock' : 'recover')}>{identityExists ? 'Desbloquear con mi frase' : 'Recuperar identidad'}</NativeRippleButton>
                </YStack>}
                {mode && <YStack gap={12}>
                  {(mode === 'recover' || mode === 'unlock') && <NativeRippleButton backgroundColor={theme.accent} color={theme.onAccent} fontWeight="700" height={50} onPress={() => void runWithMnemonic(mnemonic, '')}>{mode === 'recover' ? 'Recuperar identidad' : 'Desbloquear identidad'}</NativeRippleButton>}
                  {mode === 'create' && matchedPhrase && <NativeRippleButton backgroundColor={theme.accent} color={theme.onAccent} fontWeight="700" height={50} onPress={() => void runWithMnemonic(generatedPhrase, alias, avatar)}>Confirmar frase y continuar</NativeRippleButton>}
                  <XStack justifyContent="center"><SizableText color="placeholder-color" onPress={() => { setMode(null); setGeneratedPhrase(''); setMnemonic(''); setAvatar(''); setError(''); }} padding={12} role="button">Volver</SizableText></XStack>
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