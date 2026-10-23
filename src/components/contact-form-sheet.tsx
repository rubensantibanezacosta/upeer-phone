import { useState } from 'react';
import { QrCode } from '@tamagui/lucide-icons-2';
import { Input, Paragraph, Text, YStack } from 'tamagui';

import { NativeRippleButton } from '@/components/native-ripple-button';
import { QrScannerSheet } from '@/components/qr-scanner-sheet';
import { useTheme } from '@/hooks/use-theme';

export type ContactDraft = { upeerId: string; address: string; name: string };

export function parseContactInput(input: string): { draft: ContactDraft } | { error: string } {
  const value = input.trim();
  const separator = value.indexOf('@');
  if (separator < 1) return { error: 'Formato inválido. Usa: uPeerID@dirección de red.' };

  const upeerId = value.slice(0, separator).trim().toLowerCase();
  const address = value.slice(separator + 1).trim();
  if (!upeerId || !address) return { error: 'Formato incompleto. Incluye el ID y la dirección.' };
  if (address.includes('@')) return { error: 'La identidad debe tener un único separador @.' };
  if (!/^[0-9a-f]{64}$/.test(upeerId)) return { error: 'El uPeerID debe tener 64 caracteres hexadecimales.' };
  if (!/^[23][0-9a-f]{2}:/i.test(address)) return { error: 'La dirección debe empezar por 200: o 300:.' };
  return { draft: { upeerId, address, name: upeerId.slice(0, 12) } };
}

export function ContactFormSheet({ onAdd, onDone }: { onAdd: (draft: ContactDraft) => Promise<void>; onDone: () => void }) {
  const theme = useTheme();
  const [input, setInput] = useState('');
  const [name, setName] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [scanning, setScanning] = useState(false);

  const applyScanned = (value: string) => {
    setScanning(false);
    const parsed = parseContactInput(value);
    if ('error' in parsed) {
      setError('El QR no contiene una identidad uPeer válida.');
      return;
    }
    setInput(value.trim());
    setError('');
  };

  const submit = async () => {
    const parsed = parseContactInput(input);
    if ('error' in parsed) {
      setError(parsed.error);
      return;
    }
    setBusy(true);
    setError('');
    try {
      await onAdd({ ...parsed.draft, name: name.trim() || parsed.draft.name });
      setInput('');
      setName('');
      onDone();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'No se pudo guardar el contacto.');
    } finally {
      setBusy(false);
    }
  };

  return <YStack gap={12}>
    <Paragraph color={theme.placeholder} fontSize={12}>Escanea el QR de tu contacto o pega su identidad completa en formato uPeerID@dirección.</Paragraph>
    <YStack gap={6}>
      <Text color={theme.textSecondary} fontSize={11} fontWeight="600">IDENTIDAD UPEER</Text>
      <Input accessibilityLabel="Identidad upeer del contacto" autoCapitalize="none" autoCorrect={false} backgroundColor={theme.background} borderColor={theme.border} color={theme.text} fontFamily="monospace" fontSize={12} onChangeText={(value) => { setInput(value); setError(''); }} placeholder="uPeerID@dirección" placeholderTextColor={theme.placeholder} value={input} />
      <Text color={theme.placeholder} fontSize={11}>64 caracteres hexadecimales, @ y la dirección 200: o 300:.</Text>
      <NativeRippleButton accessibilityLabel="Escanear QR de contacto" backgroundColor={theme.accentSoft} color={theme.text} height={46} icon={<QrCode color={theme.onAccentSoft} size={17} />} onPress={() => setScanning(true)}>Escanear QR del contacto</NativeRippleButton>
    </YStack>
    <YStack gap={6}>
      <Text color={theme.textSecondary} fontSize={11} fontWeight="600">ALIAS</Text>
      <Input accessibilityLabel="Alias del contacto" autoCapitalize="words" backgroundColor={theme.background} borderColor={theme.border} color={theme.text} onChangeText={setName} placeholder="Nombre o apodo para identificarlo" placeholderTextColor={theme.placeholder} value={name} />
    </YStack>
    {error ? <Paragraph accessibilityRole="alert" backgroundColor={theme.dangerSoft} borderColor={theme.dangerBorder} borderRadius={10} borderWidth={1} color={theme.onDangerSoft} fontSize={12} padding={10}>{error}</Paragraph> : null}
    <NativeRippleButton backgroundColor={theme.accent} color={theme.onAccent} disabled={busy || !input.trim()} fontWeight="700" height={46} onPress={() => void submit()}>{busy ? 'Guardando…' : 'Añadir contacto'}</NativeRippleButton>
    <QrScannerSheet onClose={() => setScanning(false)} onScanned={applyScanned} title="Escanear contacto" visible={scanning} />
  </YStack>;
}
