import { StyleSheet, View } from 'react-native';

import { ContactsScreen } from '@/components/contacts-screen';
import { useSession } from '@/components/session-shell';

export default function ContactsRoute() {
  const { identity, signOut } = useSession();
  return <View style={styles.root}><ContactsScreen identity={identity} onSignOut={() => void signOut()} /></View>;
}

const styles = StyleSheet.create({ root: { flex: 1 } });