import { Alert } from 'react-native';

export function confirmSignOut(onConfirm: () => void): void {
  Alert.alert(
    'Cerrar sesión',
    'Se borrarán tu identidad y todos los datos locales de este dispositivo. Esta acción no se puede deshacer.',
    [
      { text: 'Cancelar', style: 'cancel' },
      { style: 'destructive', text: 'Cerrar sesión', onPress: onConfirm },
    ],
  );
}
