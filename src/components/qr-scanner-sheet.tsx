import { useCallback, useState } from 'react';
import { Modal, Pressable, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { CameraView, useCameraPermissions } from 'expo-camera';
import { Camera, X } from '@tamagui/lucide-icons-2';
import { SizableText, Text, XStack, YStack } from 'tamagui';

import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

type QrScannerSheetProps = {
  visible: boolean;
  title: string;
  onClose: () => void;
  onScanned: (value: string) => void;
};

export function QrScannerSheet({ visible, title, onClose, onScanned }: QrScannerSheetProps) {
  const theme = useTheme();
  const scale = useTextScale();
  const insets = useSafeAreaInsets();
  const [permission, requestPermission] = useCameraPermissions();
  const [handled, setHandled] = useState(false);

  const handleBarcode = useCallback((data: string) => {
    if (handled) return;
    const value = data.trim();
    if (!value) return;
    setHandled(true);
    onScanned(value);
  }, [handled, onScanned]);

  const close = () => {
    setHandled(false);
    onClose();
  };

  return <Modal animationType="fade" onRequestClose={close} presentationStyle="overFullScreen" statusBarTranslucent transparent visible={visible}>
    <View style={styles.overlay}>
      <Pressable accessibilityLabel="Cerrar escáner" onPress={close} style={[StyleSheet.absoluteFill, { backgroundColor: theme.backdrop }]} />
      <View accessibilityViewIsModal style={[styles.sheet, { backgroundColor: theme.backgroundElement, borderColor: theme.border, paddingBottom: Math.max(insets.bottom, 12) }]}>
        <XStack alignItems="center" gap={12} paddingHorizontal={16} paddingVertical={14}>
          <Camera color={theme.accent} size={20} />
          <SizableText color={theme.text} flex={1} fontSize={scale(16)} fontWeight="700" numberOfLines={1}>{title}</SizableText>
          <NativeRipplePressable accessibilityLabel="Cerrar escáner" onPress={close} style={styles.closeButton}><X color={theme.placeholder} size={18} /></NativeRipplePressable>
        </XStack>
        <View style={[styles.divider, { backgroundColor: theme.border }]} />
        {!permission
          ? <YStack paddingHorizontal={16} paddingVertical={16}>
            <Text color={theme.placeholder} fontSize={scale(12)}>Comprobando el permiso de cámara…</Text>
          </YStack>
          : permission.granted
            ? <YStack gap={10} paddingHorizontal={16} paddingVertical={14}>
              <View style={[styles.frame, { borderColor: theme.border }]}>
                <CameraView
                  barcodeScannerSettings={{ barcodeTypes: ['qr'] }}
                  facing="back"
                  onBarcodeScanned={handled ? undefined : (result) => handleBarcode(result.data)}
                  style={StyleSheet.absoluteFill}
                />
              </View>
              <Text color={theme.placeholder} fontSize={scale(12)} textAlign="center">Apunta al código QR que muestra el otro dispositivo.</Text>
            </YStack>
            : <YStack gap={10} paddingHorizontal={16} paddingVertical={14}>
              <Text color={theme.placeholder} fontSize={scale(12)}>Para escanear el código hace falta permiso de cámara. También puedes pegar el código a mano.</Text>
              <NativeRipplePressable accessibilityRole="button" onPress={() => void requestPermission()} style={styles.action}>
                <Camera color={theme.accent} size={19} />
                <Text color={theme.text} fontSize={scale(14)} fontWeight="600">Permitir cámara</Text>
              </NativeRipplePressable>
            </YStack>}
      </View>
    </View>
  </Modal>;
}

const styles = StyleSheet.create({
  overlay: { flex: 1, justifyContent: 'flex-end', paddingHorizontal: 12 },
  sheet: { borderRadius: 18, borderWidth: StyleSheet.hairlineWidth, elevation: 14, overflow: 'hidden', shadowColor: '#000', shadowOffset: { height: 8, width: 0 }, shadowOpacity: 0.32, shadowRadius: 18 },
  frame: { aspectRatio: 1, borderRadius: 14, borderWidth: StyleSheet.hairlineWidth, overflow: 'hidden', width: '100%' },
  action: { alignItems: 'center', flexDirection: 'row', gap: 14, minHeight: 52, paddingHorizontal: 18 },
  closeButton: { alignItems: 'center', borderRadius: 20, height: 40, justifyContent: 'center', width: 40 },
  divider: { height: StyleSheet.hairlineWidth, marginHorizontal: 16 },
});
