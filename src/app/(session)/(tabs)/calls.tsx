import { StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { CallsScreen } from '@/components/account-sections';

export default function CallsRoute() {
  return <SafeAreaView edges={['top']} style={styles.root}><CallsScreen /></SafeAreaView>;
}

const styles = StyleSheet.create({ root: { flex: 1 } });