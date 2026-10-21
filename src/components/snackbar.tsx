import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { Animated, Modal, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { CheckCircle2 } from '@tamagui/lucide-icons-2';
import { Text } from 'tamagui';

import { useTheme } from '@/hooks/use-theme';

type SnackbarContextValue = { showSnackbar: (message: string) => void };

const SnackbarContext = createContext<SnackbarContextValue | null>(null);

export function SnackbarProvider({ children }: { children: ReactNode }) {
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  const [message, setMessage] = useState('');
  const [visible, setVisible] = useState(false);
  const [opacity] = useState(() => new Animated.Value(0));
  const [translateY] = useState(() => new Animated.Value(12));
  const timeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const hide = useCallback(() => {
    Animated.parallel([
      Animated.timing(opacity, { toValue: 0, duration: 160, useNativeDriver: true }),
      Animated.timing(translateY, { toValue: 12, duration: 160, useNativeDriver: true }),
    ]).start(({ finished }) => {
      if (finished) setVisible(false);
    });
  }, [opacity, translateY]);

  const showSnackbar = useCallback((nextMessage: string) => {
    if (timeoutRef.current) clearTimeout(timeoutRef.current);
    opacity.stopAnimation();
    translateY.stopAnimation();
    setMessage(nextMessage);
    setVisible(true);
    Animated.parallel([
      Animated.timing(opacity, { toValue: 1, duration: 180, useNativeDriver: true }),
      Animated.spring(translateY, { toValue: 0, damping: 18, mass: 0.8, stiffness: 180, useNativeDriver: true }),
    ]).start();
    timeoutRef.current = setTimeout(hide, 2_400);
  }, [hide, opacity, translateY]);

  useEffect(() => () => {
    if (timeoutRef.current) clearTimeout(timeoutRef.current);
  }, []);

  return (
    <SnackbarContext.Provider value={{ showSnackbar }}>
      <View style={styles.root}>
        {children}
        {visible ? (
          <Modal animationType="none" onRequestClose={hide} presentationStyle="overFullScreen" statusBarTranslucent transparent visible={visible}>
            <View pointerEvents="none" style={[styles.overlay, { paddingBottom: Math.max(insets.bottom, 12) + 88 }]}>
              <Animated.View
                accessibilityLiveRegion="polite"
                accessibilityRole="alert"
                pointerEvents="none"
                style={[styles.snackbar, { backgroundColor: theme.backgroundSelected, borderColor: theme.accent, opacity, transform: [{ translateY }] }]}
              >
                <CheckCircle2 color={theme.accent} size={18} />
                <Text color={theme.text} fontSize={13} fontWeight="600">{message}</Text>
              </Animated.View>
            </View>
          </Modal>
        ) : null}
      </View>
    </SnackbarContext.Provider>
  );
}

export function useSnackbar() {
  const context = useContext(SnackbarContext);
  if (!context) throw new Error('useSnackbar debe usarse dentro de SnackbarProvider.');
  return context;
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  overlay: { flex: 1, justifyContent: 'flex-end', paddingHorizontal: 24 },
  snackbar: { alignItems: 'center', borderRadius: 12, borderWidth: 1, elevation: 8, flexDirection: 'row', gap: 8, justifyContent: 'center', minHeight: 48, paddingHorizontal: 16, paddingVertical: 12, shadowColor: '#000', shadowOffset: { height: 4, width: 0 }, shadowOpacity: 0.3, shadowRadius: 10 },
});
