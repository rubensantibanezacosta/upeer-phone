import { useEffect, useState } from 'react';
import { ActivityIndicator, StyleSheet } from 'react-native';
import { Stack, router } from 'expo-router';

import { SessionShell } from '@/components/session-shell';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import type { UPeerIdentity } from '@/domain/identity';
import { loadIdentity } from '@/storage/identity-store';

export default function AuthenticatedLayout() {
  const [identity, setIdentity] = useState<UPeerIdentity | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadIdentity().then((stored) => {
      if (stored) setIdentity(stored);
      else router.replace('/');
    }).catch((cause: unknown) => {
      console.warn('No se pudo cargar la identidad local.', cause);
      router.replace('/');
    }).finally(() => setLoading(false));
  }, []);

  if (loading) return <ThemedView style={styles.loading}><ActivityIndicator /><ThemedText>Preparando uPeer…</ThemedText></ThemedView>;
  if (!identity) return null;

  return <SessionShell key={identity.upeerId} identity={identity} onSignOut={() => { setIdentity(null); router.replace('/'); }}><Stack screenOptions={{ headerShown: false }}>
    <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
    <Stack.Screen name="settings" options={{ headerShown: false }} />
    <Stack.Screen name="chat/[upeerId]" options={{ headerShown: false }} />
    <Stack.Screen name="contact/[upeerId]" options={{ headerShown: false }} />
  </Stack></SessionShell>;
}

const styles = StyleSheet.create({ loading: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24, gap: 16 } });