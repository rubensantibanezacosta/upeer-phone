import { Link } from 'expo-router';
import { Pressable, ScrollView, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { ArrowLeft, Bell, ChevronRight, HardDrive, Info, LockKeyhole, Network, Palette, ShieldCheck, ShieldOff } from '@tamagui/lucide-icons-2';
import { H1, SizableText, Text, YStack } from 'tamagui';

import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';
import { NativeRipplePressable } from '@/components/native-ripple-pressable';

const settingsSections = [
  { id: 'privacidad', label: 'Privacidad', description: 'Lecturas, presencia y última vez', icon: LockKeyhole },
  { id: 'seguridad', label: 'Seguridad', description: 'Dispositivos y acceso a la cuenta', icon: ShieldCheck },
  { id: 'bloqueados', label: 'Bloqueados', description: 'Contactos que no pueden escribirte', icon: ShieldOff },
  { id: 'notificaciones', label: 'Notificaciones', description: 'Mensajes, solicitudes y sonidos', icon: Bell },
  { id: 'apariencia', label: 'Apariencia', description: 'Tema de la aplicación', icon: Palette },
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
      <YStack gap={4}>{settingsSections.map((item) => <Link key={item.id} href={{ pathname: '/settings/[section]', params: { section: item.id } }} asChild>
        <NativeRipplePressable accessibilityRole="button" style={StyleSheet.flatten([styles.row, { backgroundColor: theme.backgroundElement }])}>
          <item.icon color={theme.placeholder} size={22} /><YStack flex={1} gap={3}><SizableText color={theme.text} fontSize={scale(15)} fontWeight="600">{item.label}</SizableText><Text color={theme.placeholder} fontSize={scale(12)}>{item.description}</Text></YStack><ChevronRight color={theme.placeholder} size={15} />
        </NativeRipplePressable>
      </Link>)}</YStack>
    </ScrollView>
  </SafeAreaView>;
}

const styles = StyleSheet.create({ root: { flex: 1 }, content: { gap: 12, paddingHorizontal: 16, paddingTop: 18, paddingBottom: 24 }, back: { alignItems: 'center', flexDirection: 'row', gap: 8, minHeight: 40, paddingHorizontal: 4 }, row: { alignItems: 'center', borderRadius: 12, flexDirection: 'row', gap: 14, minHeight: 66, overflow: 'hidden', paddingHorizontal: 14, paddingVertical: 10 } });