import { useCallback, useEffect, useState } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView, StyleSheet, View } from 'react-native';
import { router, useLocalSearchParams } from 'expo-router';
import { SafeAreaView } from 'react-native-safe-area-context';
import { ArrowLeft, Send } from '@tamagui/lucide-icons-2';
import { Input, SizableText, Text, XStack, YStack } from 'tamagui';

import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import type { ProtocolMessage } from '../../../../modules/upeer-transport/src/UPeerTransport.types';
import { getMessages, markConversationRead, sendChat } from '../../../../modules/upeer-transport/src/UPeerTransportModule';
import { getContacts } from '@/storage/contact-store';
import { useTheme } from '@/hooks/use-theme';

const statusLabels: Record<ProtocolMessage['status'], string> = {
  pending: 'Pendiente',
  sent: 'Enviado',
  delivered: 'Entregado',
  read: 'Leído',
  failed: 'Fallido',
};

export default function ChatRoute() {
  const theme = useTheme();
  const { upeerId } = useLocalSearchParams<{ upeerId: string }>();
  const [messages, setMessages] = useState<ProtocolMessage[]>([]);
  const [alias, setAlias] = useState('');
  const [text, setText] = useState('');
  const [error, setError] = useState('');

  const refresh = useCallback(async () => {
    if (!upeerId) return;
    const [nextMessages, contacts] = await Promise.all([getMessages(upeerId, 200), getContacts()]);
    setMessages(nextMessages);
    setAlias(contacts.find((contact) => contact.upeerId === upeerId)?.name ?? upeerId.slice(0, 12));
    await markConversationRead(upeerId);
  }, [upeerId]);

  useEffect(() => {
    if (!upeerId) return;
    const initial = setTimeout(() => {
      void refresh().catch((cause: unknown) => {
        setError(cause instanceof Error ? cause.message : 'No se pudo cargar la conversación.');
      });
    }, 0);
    const timer = setInterval(() => {
      void refresh().catch((cause: unknown) => console.warn('No se pudo actualizar el chat.', cause));
    }, 1500);
    return () => {
      clearTimeout(initial);
      clearInterval(timer);
    };
  }, [refresh, upeerId]);

  const submit = async () => {
    const content = text.trim();
    if (!content || !upeerId) return;
    setText('');
    setError('');
    try {
      await sendChat(upeerId, content);
      await refresh();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'No se pudo enviar el mensaje.');
    }
  };

  return (
    <SafeAreaView edges={['top', 'bottom']} style={[styles.root, { backgroundColor: theme.background }]}>
      <XStack alignItems="center" borderBottomColor={theme.border} borderBottomWidth={StyleSheet.hairlineWidth} gap={10} paddingHorizontal={12} paddingVertical={10}>
        <NativeRipplePressable accessibilityLabel="Volver" onPress={() => router.back()} style={styles.backButton}><ArrowLeft color={theme.text} size={20} /></NativeRipplePressable>
        <YStack flex={1} gap={2}>
          <SizableText color={theme.text} fontSize={16} fontWeight="700" numberOfLines={1}>{alias || 'Conversación'}</SizableText>
          <Text color={theme.placeholder} fontSize={11} numberOfLines={1}>{upeerId}</Text>
        </YStack>
      </XStack>
      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined} style={styles.root}>
        <ScrollView contentContainerStyle={styles.messageContent}>
          {messages.length === 0 ? <YStack alignItems="center" flex={1} gap={8} justifyContent="center" padding={24}><SizableText color={theme.text} fontSize={16} fontWeight="600">Sin mensajes</SizableText><Text color={theme.placeholder} textAlign="center">El primer mensaje iniciará el handshake y la sesión cifrada.</Text></YStack> : messages.map((message) => (
            <View key={message.id} style={[styles.bubble, { alignSelf: message.isMine ? 'flex-end' : 'flex-start', backgroundColor: message.isMine ? theme.accent : theme.backgroundElement }]}>
              <Text color={message.isMine ? theme.background : theme.text} fontSize={14} lineHeight={20}>{message.content}</Text>
              <Text color={message.isMine ? theme.background : theme.placeholder} fontSize={10} opacity={0.8} textAlign="right">{statusLabels[message.status] ?? message.status} · {new Date(message.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</Text>
            </View>
          ))}
        </ScrollView>
        {!!error && <Text color={theme.danger} fontSize={12} paddingHorizontal={14} paddingVertical={6}>{error}</Text>}
        <XStack alignItems="center" borderTopColor={theme.border} borderTopWidth={StyleSheet.hairlineWidth} gap={8} paddingHorizontal={10} paddingVertical={8}>
          <Input accessibilityLabel="Mensaje" flex={1} backgroundColor={theme.backgroundElement} borderColor={theme.border} color={theme.text} multiline onChangeText={setText} onSubmitEditing={() => { void submit(); }} placeholder="Escribe un mensaje" placeholderTextColor={theme.placeholder} value={text} />
          <NativeRipplePressable accessibilityLabel="Enviar mensaje" disabled={!text.trim()} onPress={() => { void submit(); }} style={[styles.sendButton, { backgroundColor: text.trim() ? theme.accent : theme.backgroundSelected }]}><Send color={text.trim() ? theme.background : theme.placeholder} size={19} /></NativeRipplePressable>
        </XStack>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({ root: { flex: 1 }, backButton: { alignItems: 'center', borderRadius: 22, height: 44, justifyContent: 'center', width: 44 }, messageContent: { flexGrow: 1, gap: 8, justifyContent: 'flex-end', paddingHorizontal: 12, paddingVertical: 14 }, bubble: { borderRadius: 14, gap: 4, maxWidth: '84%', paddingHorizontal: 12, paddingVertical: 8 }, sendButton: { alignItems: 'center', borderRadius: 22, height: 44, justifyContent: 'center', width: 44 } });
