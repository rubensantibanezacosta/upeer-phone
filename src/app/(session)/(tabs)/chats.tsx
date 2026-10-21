import { useEffect, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { router } from 'expo-router';
import { SafeAreaView } from 'react-native-safe-area-context';
import { ChevronRight, Ellipsis, MessageCircle, MessageSquarePlus, Search, Settings, Users } from '@tamagui/lucide-icons-2';
import { Avatar, H1, Paragraph, SizableText, Text, XStack, YStack } from 'tamagui';

import { useTheme } from '@/hooks/use-theme';
import type { UPeerContact } from '@/storage/contact-store';
import { getContacts } from '@/storage/contact-store';
import type { ProtocolConversation } from '../../../../modules/upeer-transport/src/UPeerTransport.types';
import { getConversations } from '../../../../modules/upeer-transport/src/UPeerTransportModule';
import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { NativeRippleButton } from '@/components/native-ripple-button';

export default function ChatsRoute() {
  const theme = useTheme();
  const [contacts, setContacts] = useState<UPeerContact[]>([]);
  const [conversations, setConversations] = useState<ProtocolConversation[]>([]);
  const [menuOpen, setMenuOpen] = useState(false);
  const [filter, setFilter] = useState<'Todos' | 'No leídos' | 'Favoritos' | 'Grupos'>('Todos');

  useEffect(() => {
    getContacts().then(setContacts).catch((cause: unknown) => console.warn('No se pudieron cargar los contactos para Chats.', cause));
    const loadConversations = () => {
      getConversations().then(setConversations).catch((cause: unknown) => console.warn('No se pudieron cargar las conversaciones nativas.', cause));
    };
    loadConversations();
    const timer = setInterval(loadConversations, 2000);
    return () => clearInterval(timer);
  }, []);

  return (
    <SafeAreaView edges={['top']} style={[styles.root, { backgroundColor: theme.background }]}>
      <ScrollView contentContainerStyle={styles.chatContent}>
        <XStack alignItems="center" justifyContent="space-between">
          <H1 color={theme.text} fontSize={24} fontWeight="700">uPeer</H1>
          <NativeRipplePressable accessibilityLabel="Más opciones" accessibilityRole="button" onPress={() => setMenuOpen((value) => !value)} style={styles.chatMenuButton}><Ellipsis color={theme.text} size={22} /></NativeRipplePressable>
        </XStack>
        <XStack alignItems="center" backgroundColor={theme.backgroundElement} borderColor={theme.border} borderRadius={14} borderWidth={1} gap={10} paddingHorizontal={14} paddingVertical={11}><Search color={theme.placeholder} size={17} /><Text color={theme.placeholder} fontSize={14}>Buscar conversaciones</Text></XStack>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.chatFiltersScroll} contentContainerStyle={styles.chatFilters}>
          {(['Todos', 'No leídos', 'Favoritos', 'Grupos'] as const).map((item) => <NativeRipplePressable key={item} onPress={() => setFilter(item)} style={[styles.chatFilterChip, { backgroundColor: filter === item ? theme.backgroundSelected : theme.background, borderColor: filter === item ? theme.accent : theme.border }]}><Text color={filter === item ? theme.accent : theme.placeholder} fontSize={11} fontWeight={filter === item ? '700' : '500'}>{item}</Text>
          </NativeRipplePressable>)}
        </ScrollView>
        {conversations.length > 0 ? <>{conversations.map((conversation) => <NativeRipplePressable key={conversation.upeerId} accessibilityRole="button" onPress={() => router.push({ pathname: '/chat/[upeerId]', params: { upeerId: conversation.upeerId } })}><XStack alignItems="center" borderBottomColor={theme.border} borderBottomWidth={StyleSheet.hairlineWidth} gap={13} paddingVertical={14}><Avatar backgroundColor={theme.backgroundSelected} borderRadius={8} size="$5"><SizableText color={theme.accent} fontSize={17} fontWeight="700">{(conversation.alias || conversation.upeerId).charAt(0).toUpperCase()}</SizableText></Avatar><YStack flex={1} gap={4}><XStack alignItems="center" gap={8}><SizableText color={theme.text} flex={1} fontSize={15} fontWeight="600" numberOfLines={1}>{conversation.alias || conversation.upeerId.slice(0, 12)}</SizableText>{conversation.unread > 0 && <SizableText color={theme.accent} fontSize={11} fontWeight="700">{conversation.unread}</SizableText>}</XStack><Text color={theme.placeholder} fontSize={12} numberOfLines={1}>{conversation.lastMessage}</Text></YStack><ChevronRight color={theme.placeholder} size={14} /></XStack></NativeRipplePressable>)}</> : contacts.length > 0 ? <>{contacts.map((contact) => <NativeRipplePressable key={contact.upeerId} accessibilityRole="button" onPress={() => router.push({ pathname: '/chat/[upeerId]', params: { upeerId: contact.upeerId } })}><XStack alignItems="center" borderBottomColor={theme.border} borderBottomWidth={StyleSheet.hairlineWidth} gap={13} paddingVertical={14}><Avatar backgroundColor={theme.backgroundSelected} borderRadius={8} size="$5"><SizableText color={theme.accent} fontSize={17} fontWeight="700">{contact.name.charAt(0).toUpperCase()}</SizableText></Avatar><YStack flex={1} gap={4}><SizableText color={theme.text} fontSize={15} fontWeight="600">{contact.name}</SizableText><Text color={theme.placeholder} fontSize={12}>{contact.status}</Text></YStack><ChevronRight color={theme.placeholder} size={14} /></XStack></NativeRipplePressable>)}</> : <YStack alignItems="center" flex={1} gap={12} justifyContent="center" padding={24}><View style={[styles.emptyAvatar, { backgroundColor: theme.backgroundElement }]}><MessageCircle color={theme.placeholder} size={28} /></View><SizableText color={theme.text} fontSize={17} fontWeight="600">Tus conversaciones</SizableText><Paragraph color={theme.placeholder} lineHeight={1.5} textAlign="center">Añade un contacto para empezar. Tus mensajes y conversaciones se mostrarán en esta lista.</Paragraph><NativeRippleButton onPress={() => router.navigate('/contacts')}>Añadir contacto</NativeRippleButton></YStack>}
      </ScrollView>
      {menuOpen ? <><Pressable accessibilityLabel="Cerrar menú" onPress={() => setMenuOpen(false)} style={styles.menuDismiss} /><View style={[styles.chatMenu, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}><NativeRipplePressable accessibilityRole="button" onPress={() => { setMenuOpen(false); router.navigate('/contacts'); }} style={styles.chatMenuItem}><Users color={theme.text} size={18} /><Text color={theme.text} fontSize={14}>Contactos</Text></NativeRipplePressable><NativeRipplePressable accessibilityRole="button" onPress={() => { setMenuOpen(false); router.navigate('/settings'); }} style={styles.chatMenuItem}><Settings color={theme.text} size={18} /><Text color={theme.text} fontSize={14}>Configuración</Text></NativeRipplePressable></View></> : null}
      <NativeRipplePressable accessibilityLabel="Añadir contacto" accessibilityRole="button" onPress={() => router.navigate('/contacts')} style={[styles.floatingAddButton, { backgroundColor: theme.accent }]}><MessageSquarePlus color={theme.background} size={25} strokeWidth={2} /></NativeRipplePressable>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({ root: { flex: 1 }, chatContent: { flexGrow: 1, gap: 12, paddingTop: 20, paddingBottom: 18 }, chatFiltersScroll: { flexGrow: 0 }, chatFilters: { alignItems: 'center', gap: 8 }, chatFilterChip: { alignItems: 'center', borderRadius: 18, borderWidth: StyleSheet.hairlineWidth, justifyContent: 'center', minHeight: 30, paddingHorizontal: 15, marginTop:8 }, chatMenuButton: { alignItems: 'center', borderRadius: 22, height: 44, justifyContent: 'center', width: 44 }, menuDismiss: { ...StyleSheet.absoluteFill, zIndex: 1 }, chatMenu: { borderRadius: 12, borderWidth: StyleSheet.hairlineWidth, elevation: 8, paddingVertical: 6, position: 'absolute', right: -20, top: 72, width: 190, zIndex: 2 }, chatMenuItem: { alignItems: 'center', flexDirection: 'row', gap: 12, minHeight: 46, paddingHorizontal: 14 }, emptyAvatar: { alignItems: 'center', borderRadius: 32, height: 64, justifyContent: 'center', width: 64 }, floatingAddButton: { alignItems: 'center', borderRadius: 30, bottom: 12, elevation: 6, height: 58, justifyContent: 'center', position: 'absolute', right: -20, width: 58, zIndex: 3 } });