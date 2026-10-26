import { Link } from 'expo-router';
import { Pressable, ScrollView, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { ArrowLeft, Bell, ChevronRight, HardDrive, Info, LockKeyhole, Network, Palette, ShieldOff, Smartphone } from '@tamagui/lucide-icons-2';
import { H1, SizableText, Text, YStack } from 'tamagui';

import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';
import { NativeRipplePressable } from '@/components/native-ripple-pressable';

const settingsSections = [
  { id: 'dispositivos', label: 'Dispositivos', description: 'Añadir y revisar los de tu cuenta', icon: Smartphone },
  { id: 'privacidad', label: 'Privacidad', description: 'Lecturas, presencia y última vez', icon: LockKeyhole },
  { id: 'bloqueados', label: 'Bloqueados', description: 'Contactos que no pueden escribirte', icon: ShieldOff },
  { id: 'notificaciones', label: 'Notificaciones', description: 'Mensajes, solicitudes y sonidos', icon: Bell },
  { id: 'apariencia', label: 'Apariencia', description: 'Tema y tamaño de texto', icon: Palette },
  { id: 'almacenamiento', label: 'Almacenamiento', description: 'Mensajes guardados y archivos', icon: HardDrive },
  { id: 'red', label: 'Conexión', description: 'Estado y dirección de red', icon: Network },
  { id: 'acerca', label: 'Acerca de', description: 'Versión y licencia', icon: Info },
] as const;

export default function SettingsIndexRoute() {
  const theme = useTheme();
  const scale = useTextScale();

  return <SafeAreaView style={[styles.root, { backgroundColor: theme.background }]}>
    <ScrollView contentContainerStyle={styles.content}>
      <Link href="/chats" asChild>
        <Pressable accessibilityRole="button" style={styles.back}>
          <ArrowLeft color={theme.text} size={18} /><Text color={theme.text} fontSize={scale(15)}>Volver a chats</Text>
        </Pressable>
      </Link>
      <YStack gap={4} paddingBottom={8}><H1 color={theme.text} fontSize={scale(24)}>Configuración</H1><Text color={theme.placeholder} fontSize={scale(14)}>Elige una sección para administrar tu cuenta</Text></YStack>
      <YStack backgroundColor={theme.backgroundElement} borderRadius={14} overflow="hidden">
        {settingsSections.map((item, index) => <Link key={item.id} href={{ pathname: '/settings/[section]', params: { section: item.id } }} asChild>
          <NativeRipplePressable accessibilityLabel={item.label} accessibilityRole="button" style={StyleSheet.flatten([styles.row, index === 0 ? null : styles.separator, index === 0 ? null : { borderTopColor: theme.border }])}>
            <YStack alignItems="center" height={40} justifyContent="center" width={40}><item.icon color={theme.placeholder} size={20} /></YStack>
            <YStack flex={1} gap={2}><SizableText color={theme.text} fontSize={scale(15)} fontWeight="500">{item.label}</SizableText><Text color={theme.placeholder} fontSize={scale(12)} numberOfLines={1}>{item.description}</Text></YStack>
            <ChevronRight color={theme.placeholder} size={15} />
          </NativeRipplePressable>
        </Link>)}
      </YStack>
    </ScrollView>
  </SafeAreaView>;
}

const styles = StyleSheet.create({ root: { flex: 1 }, content: { gap: 12, paddingHorizontal: 16, paddingTop: 18, paddingBottom: 24 }, back: { alignItems: 'center', flexDirection: 'row', gap: 8, minHeight: 40 }, row: { alignItems: 'center', flexDirection: 'row', gap: 12, minHeight: 68, paddingHorizontal: 14, paddingVertical: 10 }, separator: { borderTopWidth: StyleSheet.hairlineWidth } });