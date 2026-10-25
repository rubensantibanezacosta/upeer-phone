import { useEffect, useState } from 'react';
import { ActivityIndicator, StyleSheet, Text } from 'react-native';
import { router } from 'expo-router';
import { SafeAreaView } from 'react-native-safe-area-context';

import { AuthScreen } from '@/components/auth-screen';
import { ThemedView } from '@/components/themed-view';
import { useTheme } from '@/hooks/use-theme';
import type { UPeerIdentity } from '@/domain/identity-record';
import { loadIdentity } from '@/storage/identity-store';

export default function HomeScreen() {
  const theme = useTheme();
  const [identity, setIdentity] = useState<UPeerIdentity | null>(null);
  const [identityExists, setIdentityExists] = useState(false);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadIdentity().then((stored) => {
      setIdentity(stored);
      setIdentityExists(stored !== null);
      if (stored) router.replace('/chats');
    }).catch((cause: unknown) => {
      console.warn('No se pudo cargar la identidad local.', cause);
    }).finally(() => setLoading(false));
  }, []);

  if (loading) {
    return <SafeAreaView style={styles.loading}>
      <ThemedView style={styles.loadingContent}><ActivityIndicator />
      <Text style={[styles.loadingText, { color: theme.text }]}>Preparando uPeer…</Text>
    </ThemedView>
    </SafeAreaView>;
  }

  return identity
    ? null
    : <AuthScreen identityExists={identityExists} onAuthenticated={(nextIdentity) => { setIdentity(nextIdentity); setIdentityExists(true); router.replace('/chats'); }} />;
}

const styles = StyleSheet.create({
  loading: { flex: 1 },
  loadingContent: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24, gap: 16 },
  loadingText: { fontSize: 16, textAlign: 'center' },
});