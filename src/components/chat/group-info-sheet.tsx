import { useCallback, useEffect, useState } from 'react';
import { Alert, StyleSheet, View } from 'react-native';
import { Crown, LogOut, Pencil, UserMinus, UserPlus, Users } from '@tamagui/lucide-icons-2';
import { Input, SizableText, Text, XStack, YStack } from 'tamagui';

import { BottomSheet } from '@/components/bottom-sheet';
import { ContactPicker } from '@/components/contact-picker';
import { InteractiveRow } from '@/components/contact-info/interactive-row';
import { NativeRippleButton } from '@/components/native-ripple-button';
import { useSession } from '@/components/session-shell';
import { useSnackbar } from '@/components/snackbar';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';
import { getContacts, type UPeerContact } from '@/storage/contact-store';
import type { ProtocolGroup } from '../../../modules/upeer-transport/src/UPeerTransport.types';
import {
  getGroups,
  inviteToGroup,
  leaveGroup,
  removeGroupMember,
  renameGroup,
  transferGroupAdmin,
} from '../../../modules/upeer-transport/src/UPeerTransportModule';

type GroupInfoSheetProps = {
  groupId: string | null;
  onChanged?: () => void;
  onClose: () => void;
};

type SheetMode = 'info' | 'invite' | 'member';

export function GroupInfoSheet({ groupId, onChanged, onClose }: GroupInfoSheetProps) {
  const theme = useTheme();
  const scale = useTextScale();
  const { showSnackbar } = useSnackbar();
  const { identity } = useSession();
  const [group, setGroup] = useState<ProtocolGroup | null>(null);
  const [contacts, setContacts] = useState<UPeerContact[]>([]);
  const [mode, setMode] = useState<SheetMode>('info');
  const [selected, setSelected] = useState<string[]>([]);
  const [member, setMember] = useState<string | null>(null);
  const [renaming, setRenaming] = useState(false);
  const [draft, setDraft] = useState('');
  const [busy, setBusy] = useState(false);

  const load = useCallback(async (target: string) => {
    const [groups, list] = await Promise.all([getGroups(), getContacts()]);
    setGroup(groups.find((item) => item.groupId === target) ?? null);
    setContacts(list);
  }, []);

  useEffect(() => {
    if (!groupId) return;
    let mounted = true;
    Promise.all([getGroups(), getContacts()])
      .then(([groups, list]) => {
        if (!mounted) return;
        setGroup(groups.find((item) => item.groupId === groupId) ?? null);
        setContacts(list);
      })
      .catch((cause: unknown) => console.warn('No se pudo cargar el grupo.', cause));
    return () => {
      mounted = false;
    };
  }, [groupId]);

  const close = () => {
    setMode('info');
    setRenaming(false);
    setMember(null);
    setSelected([]);
    onClose();
  };

  const active = group?.groupId === groupId ? group : null;
  const members = active?.members ?? [];
  const selfId = identity.upeerId;
  const isAdmin = active?.adminUpeerId === selfId;
  const nameOf = (upeerId: string) => contacts.find((item) => item.upeerId === upeerId)?.name ?? `${upeerId.slice(0, 10)}…`;
  const available = contacts.filter((contact) => contact.status === 'connected' && !members.includes(contact.upeerId));

  const run = async (action: () => Promise<void>, success: string) => {
    if (busy) return;
    setBusy(true);
    try {
      await action();
      if (groupId) await load(groupId);
      onChanged?.();
      showSnackbar(success);
      setMode('info');
      setMember(null);
      setSelected([]);
      setRenaming(false);
    } catch (cause) {
      console.warn('No se pudo actualizar el grupo.', cause);
      showSnackbar(cause instanceof Error ? cause.message : 'No se pudo actualizar el grupo');
    } finally {
      setBusy(false);
    }
  };

  const confirmLeave = () => {
    if (!groupId) return;
    Alert.alert('Salir del grupo', 'Dejarás de recibir los mensajes de este grupo en este dispositivo.', [
      { text: 'Cancelar', style: 'cancel' },
      { text: 'Salir', style: 'destructive', onPress: () => void run(() => leaveGroup(groupId), 'Has salido del grupo') },
    ]);
  };

  const confirmRemove = (target: string) => {
    if (!groupId) return;
    Alert.alert('Expulsar del grupo', `${nameOf(target)} dejará de recibir los mensajes y se rotará la clave del grupo.`, [
      { text: 'Cancelar', style: 'cancel' },
      { text: 'Expulsar', style: 'destructive', onPress: () => void run(() => removeGroupMember(groupId, target), 'Miembro expulsado') },
    ]);
  };

  const title = mode === 'invite' ? 'Invitar al grupo' : mode === 'member' ? nameOf(member ?? '') : active?.name || 'Grupo';

  return <BottomSheet icon={<Users color={theme.accent} size={20} />} onClose={close} title={title} visible={groupId !== null}>
    {mode === 'invite' ? <YStack gap={10}>
      <Text color={theme.placeholder} fontSize={scale(12)}>Solo pueden invitarse contactos ya conectados: la invitación viaja cifrada a su clave.</Text>
      <ContactPicker
        contacts={available}
        emptyLabel="No quedan contactos conectados por invitar"
        onToggle={(contact) => setSelected((current) => (current.includes(contact.upeerId)
          ? current.filter((id) => id !== contact.upeerId)
          : [...current, contact.upeerId]))}
        searchPlaceholder="Buscar contacto"
        selectedIds={selected}
      />
      <NativeRippleButton
        backgroundColor={theme.accent}
        color={theme.onAccent}
        disabled={busy || selected.length === 0}
        fontWeight="700"
        height={46}
        onPress={() => groupId && void run(async () => {
          for (const target of selected) await inviteToGroup(groupId, target);
        }, selected.length === 1 ? 'Invitación enviada' : 'Invitaciones enviadas')}
      >
        {busy ? 'Invitando…' : `Invitar${selected.length > 0 ? ` (${selected.length})` : ''}`}
      </NativeRippleButton>
    </YStack> : null}

    {mode === 'member' && member ? <YStack gap={6}>
      <Text color={theme.placeholder} fontSize={scale(11)} numberOfLines={1}>{member}</Text>
      {isAdmin ? <InteractiveRow icon={Crown} label="Transferir administración" onPress={() => groupId && void run(async () => {
        await transferGroupAdmin(groupId, member);
      }, 'Administración transferida')} /> : null}
      {isAdmin ? <InteractiveRow icon={UserMinus} label={`Expulsar a ${nameOf(member)}`} onPress={() => confirmRemove(member)} tone="danger" /> : null}
      {!isAdmin ? <Text color={theme.placeholder} fontSize={scale(12)}>Solo el administrador puede gestionar a los miembros.</Text> : null}
      <NativeRippleButton backgroundColor={theme.background} color={theme.text} height={44} onPress={() => { setMode('info'); setMember(null); }}>Volver</NativeRippleButton>
    </YStack> : null}

    {mode === 'info' ? <>
      {renaming ? <YStack gap={8}>
        <Input
          accessibilityLabel="Nombre del grupo"
          autoFocus
          backgroundColor={theme.background}
          borderColor={theme.border}
          color={theme.text}
          onChangeText={setDraft}
          value={draft}
        />
        <XStack gap={8}>
          <NativeRippleButton backgroundColor={theme.background} color={theme.text} flex={1} height={44} onPress={() => setRenaming(false)}>Cancelar</NativeRippleButton>
          <NativeRippleButton
            backgroundColor={theme.accent}
            color={theme.onAccent}
            disabled={busy || draft.trim().length === 0}
            flex={1}
            fontWeight="700"
            height={44}
            onPress={() => groupId && void run(() => renameGroup(groupId, draft.trim()), 'Grupo renombrado')}
          >
            {busy ? 'Guardando…' : 'Guardar'}
          </NativeRippleButton>
        </XStack>
      </YStack> : null}
      <XStack alignItems="center" gap={8}>
        <Text color={theme.placeholder} flex={1} fontSize={scale(12)}>{`${members.length} ${members.length === 1 ? 'miembro' : 'miembros'}`}</Text>
        {active?.status === 'invited' ? <Text color={theme.accent} fontSize={scale(12)}>Invitación pendiente</Text> : null}
      </XStack>
      <YStack gap={6}>
        {members.map((upeerId) => <NativeRippleButton
          accessibilityLabel={`Opciones de ${nameOf(upeerId)}`}
          backgroundColor={theme.background}
          borderColor={theme.border}
          borderWidth={StyleSheet.hairlineWidth}
          height="auto"
          key={upeerId}
          onPress={() => { setMember(upeerId); setMode('member'); }}
          paddingHorizontal={12}
          paddingVertical={8}
        >
          <XStack alignItems="center" gap={10} width="100%">
            <View style={[styles.badge, { backgroundColor: theme.backgroundElement }]}>
              <Text color={theme.placeholder} fontSize={scale(11)} fontWeight="700">{upeerId.slice(0, 2).toUpperCase()}</Text>
            </View>
            <YStack flex={1}>
              <SizableText color={theme.text} fontSize={scale(13)} numberOfLines={1}>{nameOf(upeerId)}</SizableText>
              <Text color={theme.placeholder} fontSize={scale(11)}>{upeerId === active?.adminUpeerId ? 'Administrador' : upeerId === selfId ? 'Tú' : 'Miembro'}</Text>
            </YStack>
            {upeerId === active?.adminUpeerId ? <Crown color={theme.accent} size={16} /> : null}
          </XStack>
        </NativeRippleButton>)}
        {members.length === 0 ? <Text color={theme.placeholder} fontSize={scale(12)}>Sin miembros sincronizados todavía.</Text> : null}
      </YStack>
      {isAdmin ? <>
        <InteractiveRow icon={UserPlus} label="Invitar contactos" onPress={() => { setSelected([]); setMode('invite'); }} />
        <InteractiveRow icon={Pencil} label="Renombrar grupo" onPress={() => { setDraft(active?.name ?? ''); setRenaming(true); }} />
      </> : null}
      <InteractiveRow icon={LogOut} label="Salir del grupo" onPress={confirmLeave} tone="danger" />
    </> : null}
  </BottomSheet>;
}

const styles = StyleSheet.create({
  badge: { alignItems: 'center', borderRadius: 16, height: 32, justifyContent: 'center', width: 32 },
});
