import { StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { ProfileScreen } from '@/components/account-sections';
import { useSession } from '@/components/session-shell';

export default function ProfileRoute() {
  const { identity, address, signOut, refreshProfile } = useSession();
  return <SafeAreaView edges={['top']} style={styles.root}><ProfileScreen identity={identity} address={address} onProfileUpdated={() => { void refreshProfile(); }} onSignOut={() => void signOut()} /></SafeAreaView>;
}

const styles = StyleSheet.create({ root: { flex: 1 } });