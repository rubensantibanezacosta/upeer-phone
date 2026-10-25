import { StyleSheet, View } from 'react-native';
import { Check, CheckCheck, CircleAlert, Clock3 } from '@tamagui/lucide-icons-2';

import { useTheme } from '@/hooks/use-theme';
import type { ProtocolMessage } from '../../../modules/upeer-transport/src/UPeerTransport.types';

const statusLabels: Record<ProtocolMessage['status'], string> = {
  pending: 'Pendiente',
  sent: 'Enviado',
  vaulted: 'Guardado en el vault',
  delivered: 'Entregado',
  read: 'Leído',
  failed: 'Fallido',
};

export function MessageStatus({ color, status }: { color: string; status: ProtocolMessage['status'] }) {
  const theme = useTheme();
  const iconColor = status === 'failed' ? theme.danger : status === 'read' ? theme.receiptRead : color;
  const icon = status === 'failed'
    ? <CircleAlert color={iconColor} size={14} strokeWidth={2.3} />
    : status === 'pending'
      ? <Clock3 color={iconColor} size={12} strokeWidth={2.2} />
      : status === 'sent' || status === 'vaulted'
        ? <Check color={iconColor} size={16} strokeWidth={2.3} />
        : <CheckCheck color={iconColor} size={16} strokeWidth={2.3} />;
  return <View accessibilityLabel={`Estado del mensaje: ${statusLabels[status]}`} style={styles.status}>{icon}</View>;
}

const styles = StyleSheet.create({
  status: { alignItems: 'center', justifyContent: 'center' },
});
