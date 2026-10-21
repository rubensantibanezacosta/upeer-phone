import { Link, useLocalSearchParams } from 'expo-router';
import { Pressable, ScrollView, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { ArrowLeft } from '@tamagui/lucide-icons-2';
import { Card, Paragraph, SizableText, Text } from 'tamagui';

import { ConnectionSettingsSection } from '@/components/connection-settings-section';
import { useSession } from '@/components/session-shell';
import { useCopyToClipboard } from '@/hooks/use-copy-to-clipboard';
import { useTheme } from '@/hooks/use-theme';

const sectionLabels: Record<string, string> = {
  privacidad: 'Privacidad',
  seguridad: 'Seguridad',
  notificaciones: 'Notificaciones',
  apariencia: 'Apariencia',
  almacenamiento: 'Almacenamiento',
  red: 'Conexión',
  acerca: 'Acerca de',
};

export default function SettingsSectionRoute() {
  const theme = useTheme();
  const copyToClipboard = useCopyToClipboard();
  const { connection } = useSession();
  const { section } = useLocalSearchParams<{ section: string }>();
  const label = sectionLabels[section] ?? 'Configuración';

  return <SafeAreaView edges={['top', 'bottom']} style={[styles.root, { backgroundColor: theme.background }]}>
    <ScrollView contentContainerStyle={styles.content}>
      <Link href="/settings" asChild>
        <Pressable accessibilityRole="button" style={styles.back}>
          <ArrowLeft color={theme.text} size={18} /><Text color={theme.text} fontSize={15}>Volver a configuración</Text>
        </Pressable>
      </Link>
      {section === 'red' ? <ConnectionSettingsSection {...connection} onCopy={(value) => { void copyToClipboard(value, 'Dirección copiada'); }} /> : <Card backgroundColor={theme.backgroundElement} borderColor={theme.border} borderRadius={14} borderWidth={1} gap={8} padding={16}><SizableText color={theme.text} fontSize={22} fontWeight="700">{label}</SizableText><Paragraph color={theme.placeholder}>Esta sección todavía no está disponible en la app móvil.</Paragraph></Card>}
    </ScrollView>
  </SafeAreaView>;
}

const styles = StyleSheet.create({ root: { flex: 1 }, content: { gap: 12, paddingHorizontal: 16, paddingTop: 18, paddingBottom: 24 }, back: { alignItems: 'center', flexDirection: 'row', gap: 8, minHeight: 40, paddingHorizontal: 4 } });