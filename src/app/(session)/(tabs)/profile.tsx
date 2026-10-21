import { useCallback } from 'react';
import { StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { ProfileScreen } from '@/components/account-sections';
import { useSession } from '@/components/session-shell';
import { loadProfile } from '@/storage/identity-store';

export default function ProfileRoute() {
  const { identity, address, signOut } = useSession();
  const refreshProfile = useCallback(() => {
    void loadProfile().catch((cause: unknown) => console.warn('No se pudo actualizar el perfil.', cause));
  }, []);
  return <SafeAreaView edges={['top']} style={styles.root}><ProfileScreen identity={identity} address={address} onProfileUpdated={refreshProfile} onSignOut={() => void signOut()} /></SafeAreaView>;
}

const styles = StyleSheet.create({ root: { flex: 1 } });