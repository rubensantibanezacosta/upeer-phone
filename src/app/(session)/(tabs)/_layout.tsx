import { Tabs } from 'expo-router';
import { useColorScheme as useNativeColorScheme, Pressable, type PressableProps } from 'react-native';
import { MessageCircle, Phone, UserRound, Users } from '@tamagui/lucide-icons-2';
import { Colors } from '@/constants/theme';

export default function SessionTabsLayout() {
  const colorScheme = useNativeColorScheme();
  const theme = Colors[colorScheme === 'dark' ? 'dark' : 'light'];
  return <Tabs screenOptions={{ headerShown: false, sceneStyle: { paddingHorizontal: 20 }, tabBarActiveTintColor: theme.accent, tabBarInactiveTintColor: theme.placeholder, tabBarStyle: { backgroundColor: theme.backgroundElement, borderTopColor: theme.border }, tabBarLabelStyle: { fontSize: 10 }, tabBarButton: ({ children, onPress, onLongPress, accessibilityLabel, accessibilityState, accessibilityRole, testID, style, disabled }) => <Pressable accessibilityLabel={accessibilityLabel} accessibilityRole={accessibilityRole} accessibilityState={accessibilityState} disabled={disabled} onLongPress={onLongPress} onPress={onPress as PressableProps['onPress']} style={style} testID={testID} android_ripple={{ color: 'transparent' }}>{children}</Pressable> }}>
    <Tabs.Screen name="chats" options={{ title: 'Chats', tabBarIcon: ({ color, size }) => <MessageCircle color={String(color)} size={size} /> }} />
    <Tabs.Screen name="contacts" options={{ title: 'Contactos', tabBarIcon: ({ color, size }) => <Users color={String(color)} size={size} /> }} />
    <Tabs.Screen name="calls" options={{ title: 'Llamadas', tabBarIcon: ({ color, size }) => <Phone color={String(color)} size={size} /> }} />
    <Tabs.Screen name="profile" options={{ title: 'Perfil', sceneStyle: { paddingHorizontal: 0 }, tabBarIcon: ({ color, size }) => <UserRound color={String(color)} size={size} /> }} />
  </Tabs>;
}