import { Link, useLocalSearchParams } from 'expo-router';
import { ScrollView, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { ArrowLeft } from '@tamagui/lucide-icons-2';
import { Card, Paragraph, SizableText, Text } from 'tamagui';
import * as Clipboard from 'expo-clipboard';

import { ConnectionSettingsSection } from '@/components/connection-settings-section';
import { useSession } from '@/components/session-shell';
import { useTheme } from '@/hooks/use-theme';
import { NativeRipplePressable } from '@/components/native-ripple-pressable';

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
  const { connection } = useSession();
  const { section } = useLocalSearchParams<{ section: string }>();
  const label = sectionLabels[section] ?? 'Configuración';

  return <SafeAreaView edges={['top', 'bottom']} style={[styles.root, { backgroundColor: theme.background }]}>
    <ScrollView contentContainerStyle={styles.content}>
      <Link href="/settings" asChild>
        <NativeRipplePressable accessibilityRole="button" android_ripple={{ color: theme.backgroundSelected, borderless: true }} style={styles.back}>
          <ArrowLeft color={theme.text} size={18} /><Text color={theme.text} fontSize={15}>Volver a configuración</Text>
        </NativeRipplePressable>
      </Link>
      {section === 'red' ? <ConnectionSettingsSection {...connection} onCopy={(value) => { void Clipboard.setStringAsync(value); }} /> : <Card backgroundColor={theme.backgroundElement} borderColor={theme.border} borderRadius={14} borderWidth={1} gap={8} padding={16}><SizableText color={theme.text} fontSize={22} fontWeight="700">{label}</SizableText><Paragraph color={theme.placeholder}>Esta sección todavía no está disponible en la app móvil.</Paragraph></Card>}
    </ScrollView>
  </SafeAreaView>;
}

const styles = StyleSheet.create({ root: { flex: 1 }, content: { gap: 12, paddingHorizontal: 16, paddingTop: 18, paddingBottom: 24 }, back: { alignItems: 'center', flexDirection: 'row', gap: 8, minHeight: 40, paddingHorizontal: 4 } });