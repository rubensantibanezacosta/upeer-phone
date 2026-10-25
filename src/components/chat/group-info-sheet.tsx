import { useEffect, useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { Users } from '@tamagui/lucide-icons-2';
import { SizableText, Text, XStack, YStack } from 'tamagui';

import { BottomSheet } from '@/components/bottom-sheet';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';
import type { ProtocolGroup } from '../../../modules/upeer-transport/src/UPeerTransport.types';
import { getGroups } from '../../../modules/upeer-transport/src/UPeerTransportModule';

type GroupInfoSheetProps = {
  groupId: string | null;
  onClose: () => void;
};

export function GroupInfoSheet({ groupId, onClose }: GroupInfoSheetProps) {
  const theme = useTheme();
  const scale = useTextScale();
  const [group, setGroup] = useState<ProtocolGroup | null>(null);

  useEffect(() => {
    if (!groupId) return;
    let active = true;
    getGroups()
      .then((groups) => {
        if (active) setGroup(groups.find((item) => item.groupId === groupId) ?? null);
      })
      .catch((cause: unknown) => console.warn('No se pudo cargar el grupo.', cause));
    return () => {
      active = false;
    };
  }, [groupId]);

  const members = group?.members ?? [];
  const role = (upeerId: string) => (upeerId === group?.adminUpeerId ? 'Administrador' : 'Miembro');

  return <BottomSheet icon={<Users color={theme.accent} size={20} />} onClose={onClose} title={group?.name || 'Grupo'} visible={groupId !== null}>
    <Text color={theme.placeholder} fontSize={scale(12)}>{`${members.length} ${members.length === 1 ? 'miembro' : 'miembros'}`}</Text>
    {group?.status === 'invited' ? <Text color={theme.accent} fontSize={scale(12)}>Invitación pendiente</Text> : null}
    <YStack gap={8}>
      {members.map((member) => <XStack alignItems="center" gap={10} key={member} minHeight={40}>
        <View style={[styles.badge, { backgroundColor: theme.background }]}>
          <Text color={theme.placeholder} fontSize={scale(11)} fontWeight="700">{member.slice(0, 2).toUpperCase()}</Text>
        </View>
        <YStack flex={1}>
          <SizableText color={theme.text} fontSize={scale(13)} numberOfLines={1}>{member}</SizableText>
          <Text color={theme.placeholder} fontSize={scale(11)}>{role(member)}</Text>
        </YStack>
      </XStack>)}
      {members.length === 0 ? <Text color={theme.placeholder} fontSize={scale(12)}>Sin miembros sincronizados todavía.</Text> : null}
    </YStack>
    <Text color={theme.placeholder} fontSize={scale(11)}>Los grupos se muestran en solo lectura en este dispositivo.</Text>
  </BottomSheet>;
}

const styles = StyleSheet.create({
  badge: { alignItems: 'center', borderRadius: 16, height: 32, justifyContent: 'center', width: 32 },
});
