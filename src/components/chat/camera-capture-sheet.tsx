import { useCallback, useEffect, useRef, useState } from 'react';
import { Modal, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { CameraView, useCameraPermissions } from 'expo-camera';
import { StatusBar } from 'expo-status-bar';
import { Camera, Images, RefreshCw, Video, X } from '@tamagui/lucide-icons-2';
import { Text, XStack, YStack } from 'tamagui';

import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { RecentMediaTray } from '@/components/chat/recent-media-tray';
import { loadRecentMedia, recentMediaAttachment, type RecentMediaItem } from '@/services/recent-media';
import { pickedAttachment, type PickedAttachment } from '@/services/attachment-picker';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';

const MAX_VIDEO_SECONDS = 60;

type CameraMode = 'picture' | 'video';

type CameraCaptureSheetProps = {
  onClose: () => void;
  onOpenLibrary: () => void;
  onPick: (attachment: PickedAttachment) => void;
  visible: boolean;
};

export function CameraCaptureSheet({ onClose, onOpenLibrary, onPick, visible }: CameraCaptureSheetProps) {
  const theme = useTheme();
  const scale = useTextScale();
  const insets = useSafeAreaInsets();
  const [permission, requestPermission] = useCameraPermissions();
  const [mode, setMode] = useState<CameraMode>('picture');
  const [facing, setFacing] = useState<'back' | 'front'>('back');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [recording, setRecording] = useState(false);
  const [recent, setRecent] = useState<RecentMediaItem[]>([]);
  const [loadingRecent, setLoadingRecent] = useState(false);
  const camera = useRef<CameraView | null>(null);

  const refreshRecent = useCallback(() => {
    void loadRecentMedia()
      .then((items) => {
        setRecent(items);
        setLoadingRecent(false);
      })
      .catch((cause: unknown) => {
        console.warn('No se pudieron leer las fotos recientes.', cause);
        setRecent([]);
        setLoadingRecent(false);
      });
  }, []);

  useEffect(() => {
    if (!visible) return;
    refreshRecent();
  }, [refreshRecent, visible]);

  const deliver = useCallback((attachment: PickedAttachment) => {
    onPick(attachment);
    setLoadingRecent(true);
    refreshRecent();
  }, [onPick, refreshRecent]);

  const takePicture = useCallback(async () => {
    setBusy(true);
    try {
      const photo = await camera.current?.takePictureAsync({ quality: 1 });
      if (!photo?.uri) {
        setError('La cámara no devolvió ninguna foto.');
        return;
      }
      deliver(pickedAttachment(photo.uri, undefined, 'image/jpeg'));
    } catch (cause) {
      console.warn('No se pudo hacer la foto.', cause);
      setError('No se pudo hacer la foto.');
    } finally {
      setBusy(false);
    }
  }, [deliver]);

  const stopVideo = useCallback(() => {
    camera.current?.stopRecording();
  }, []);

  const startVideo = useCallback(async () => {
    if (!camera.current) return;
    setRecording(true);
    try {
      const video = await camera.current.recordAsync({ maxDuration: MAX_VIDEO_SECONDS });
      if (video?.uri) deliver(pickedAttachment(video.uri, undefined, 'video/mp4'));
    } catch (cause) {
      console.warn('No se pudo grabar el vídeo.', cause);
      setError('No se pudo grabar el vídeo.');
    } finally {
      setRecording(false);
    }
  }, [deliver]);

  const pickRecent = useCallback(async (item: RecentMediaItem) => {
    try {
      deliver(await recentMediaAttachment(item));
    } catch (cause) {
      console.warn('No se pudo adjuntar la foto reciente.', cause);
      setError('No se pudo adjuntar la foto reciente.');
    }
  }, [deliver]);

  const handleShow = useCallback(() => {
    setError('');
    setLoadingRecent(true);
    refreshRecent();
  }, [refreshRecent]);

  const chrome = { backgroundColor: 'rgba(0,0,0,0.45)' };

  return <Modal animationType="fade" onRequestClose={onClose} onShow={handleShow} presentationStyle="overFullScreen" statusBarTranslucent transparent visible={visible}>
    <View style={[styles.root, { backgroundColor: '#000' }]}>
      <StatusBar style="light" />
      {permission?.granted
        ? <CameraView facing={facing} mode={mode} ref={camera} style={StyleSheet.absoluteFill} />
        : null}
      <View style={[styles.topBar, { paddingTop: insets.top + 8 }]}>
        <NativeRipplePressable accessibilityLabel="Cerrar cámara" onPress={onClose} style={[styles.round, chrome]}>
          <X color="#fff" size={20} />
        </NativeRipplePressable>
        <NativeRipplePressable accessibilityLabel="Cambiar de cámara" onPress={() => setFacing((current) => (current === 'back' ? 'front' : 'back'))} style={[styles.round, chrome]}>
          <RefreshCw color="#fff" size={19} />
        </NativeRipplePressable>
      </View>
      {!permission
        ? <View style={styles.center}>
          <Text color="#fff" fontSize={scale(13)} textAlign="center">Comprobando el permiso de cámara…</Text>
        </View>
        : permission.granted ? null : <View style={styles.center}>
          <YStack alignItems="center" gap={14} paddingHorizontal={28}>
            <Camera color="#fff" size={30} />
            <Text color="#fff" fontSize={scale(13)} textAlign="center">Para hacer fotos y vídeos hace falta permiso de cámara.</Text>
            <NativeRipplePressable accessibilityLabel="Permitir cámara" accessibilityRole="button" onPress={() => void requestPermission()} style={[styles.permission, { backgroundColor: theme.accent }]}>
              <Text color={theme.onAccent} fontSize={scale(14)} fontWeight="700">Permitir cámara</Text>
            </NativeRipplePressable>
          </YStack>
        </View>}
      <View style={[styles.bottom, { paddingBottom: Math.max(insets.bottom, 12) }]}>
        {error ? <Text color={theme.danger} fontSize={scale(12)} textAlign="center">{error}</Text> : null}
        <RecentMediaTray onDark items={recent} loading={loadingRecent} onPick={(item) => void pickRecent(item)} />
        <XStack alignItems="center" paddingTop={4}>
          <XStack flex={1} justifyContent="flex-start">
            <NativeRipplePressable
              accessibilityLabel={mode === 'video' ? 'Cambiar a foto' : 'Cambiar a vídeo'}
              accessibilityRole="button"
              disabled={recording}
              onPress={() => setMode((current) => (current === 'picture' ? 'video' : 'picture'))}
              style={[styles.round, { backgroundColor: mode === 'video' ? theme.danger : 'rgba(0,0,0,0.45)' }]}
            >
              {mode === 'video' ? <Camera color="#fff" size={20} /> : <Video color="#fff" size={20} />}
            </NativeRipplePressable>
          </XStack>
          <NativeRipplePressable
            accessibilityLabel={mode === 'video' ? 'Grabar vídeo' : 'Hacer foto'}
            accessibilityRole="button"
            disabled={busy}
            onPress={mode === 'video' ? (recording ? stopVideo : () => void startVideo()) : () => void takePicture()}
            style={[styles.shutter, { borderColor: recording ? theme.danger : '#fff' }]}
          >
            {mode === 'video'
              ? <View style={[styles.shutterCore, { backgroundColor: recording ? theme.danger : '#FF3B30', borderRadius: recording ? 6 : 26 }]} />
              : <View style={[styles.shutterCore, { backgroundColor: '#fff' }]} />}
          </NativeRipplePressable>
          <XStack flex={1} justifyContent="flex-end">
            <NativeRipplePressable accessibilityLabel="Abrir la galería" accessibilityRole="button" onPress={onOpenLibrary} style={[styles.round, chrome]}>
              <Images color="#fff" size={20} />
            </NativeRipplePressable>
          </XStack>
        </XStack>
      </View>
    </View>
  </Modal>;
}

const styles = StyleSheet.create({
  bottom: { bottom: 0, gap: 14, left: 0, paddingHorizontal: 12, position: 'absolute', right: 0 },
  center: { alignItems: 'center', bottom: 0, justifyContent: 'center', left: 0, position: 'absolute', right: 0, top: 0 },
  permission: { alignItems: 'center', borderRadius: 14, justifyContent: 'center', minHeight: 48, paddingHorizontal: 22 },
  root: { flex: 1 },
  round: { alignItems: 'center', borderRadius: 22, height: 44, justifyContent: 'center', width: 44 },
  shutter: { alignItems: 'center', borderRadius: 34, borderWidth: 4, height: 68, justifyContent: 'center', width: 68 },
  shutterCore: { borderRadius: 26, height: 52, width: 52 },
  topBar: { alignItems: 'center', flexDirection: 'row', justifyContent: 'space-between', left: 0, paddingHorizontal: 14, position: 'absolute', right: 0, top: 0 },
});
