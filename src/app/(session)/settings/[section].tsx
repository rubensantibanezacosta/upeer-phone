import { Link, useLocalSearchParams } from 'expo-router';
import { Pressable, ScrollView, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { ArrowLeft } from '@tamagui/lucide-icons-2';
import { Card, Paragraph, SizableText, Text } from 'tamagui';

import { renderSettingsSection, SETTINGS_SECTION_LABELS } from '@/components/settings-sections';
import { useSession } from '@/components/session-shell';
import { useCopyToClipboard } from '@/hooks/use-copy-to-clipboard';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

export default function SettingsSectionRoute() {
  const theme = useTheme();
  const scale = useTextScale();
  const copyToClipboard = useCopyToClipboard();
  const { connection, identity } = useSession();
  const { section } = useLocalSearchParams<{ section: string }>();
  const label = SETTINGS_SECTION_LABELS[section] ?? 'Configuración';
  const rendered = renderSettingsSection({ section, connection, identity, onCopy: (value) => { void copyToClipboard(value, 'Dirección copiada'); } });

  return <SafeAreaView edges={['top', 'bottom']} style={[styles.root, { backgroundColor: theme.background }]}>
    <ScrollView contentContainerStyle={styles.content}>
      <Link href="/settings" asChild>
        <Pressable accessibilityRole="button" style={styles.back}>
          <ArrowLeft color={theme.text} size={18} /><Text color={theme.text} fontSize={scale(15)}>Volver a configuración</Text>
        </Pressable>
      </Link>
      {rendered ?? <Card backgroundColor={theme.backgroundElement} borderColor={theme.border} borderRadius={14} borderWidth={1} gap={8} padding={16}><SizableText color={theme.text} fontSize={scale(22)} fontWeight="700">{label}</SizableText><Paragraph color={theme.placeholder}>Esta sección todavía no está disponible en la app móvil.</Paragraph></Card>}
    </ScrollView>
  </SafeAreaView>;
}

const styles = StyleSheet.create({ root: { flex: 1 }, content: { gap: 12, paddingHorizontal: 16, paddingTop: 18, paddingBottom: 24 }, back: { alignItems: 'center', flexDirection: 'row', gap: 8, minHeight: 40 } });