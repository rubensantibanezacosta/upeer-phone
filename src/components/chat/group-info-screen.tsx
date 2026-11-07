import { useCallback, useEffect, useState } from 'react';
import { Alert, ScrollView, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { ArrowLeft, Crown, LogOut, Pencil, UserMinus, UserPlus } from '@tamagui/lucide-icons-2';
import { Input, SizableText, Text, XStack, YStack } from 'tamagui';

import { NativeRippleButton } from '@/components/native-ripple-button';
import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { ContactPicker } from '@/components/contact-picker';
import { InteractiveRow } from '@/components/contact-info/interactive-row';
import { ProfileAvatar } from '@/components/profile-avatar';
import { useSnackbar } from '@/components/snackbar';
import { useSelfUpeerId } from '@/hooks/use-self-upeer-id';
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

type GroupInfoScreenProps = {
  groupId: string;
  onBack: () => void;
  onChanged?: () => void;
};

type ScreenMode = 'info' | 'invite' | 'member';

export function GroupInfoScreen({ groupId, onBack, onChanged }: GroupInfoScreenProps) {
  const theme = useTheme();
  const scale = useTextScale();
  const { showSnackbar } = useSnackbar();
  const [group, setGroup] = useState<ProtocolGroup | null>(null);
  const [contacts, setContacts] = useState<UPeerContact[]>([]);
  const [mode, setMode] = useState<ScreenMode>('info');
  const [selected, setSelected] = useState<string[]>([]);
  const [member, setMember] = useState<string | null>(null);
  const [renaming, setRenaming] = useState(false);
  const [draft, setDraft] = useState('');
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => Promise.all([getGroups(), getContacts()]).then(([groups, list]) => {
    setGroup(groups.find((item) => item.groupId === groupId) ?? null);
    setContacts(list);
  }), [groupId]);

  useEffect(() => {
    load().catch((cause: unknown) => {
      console.warn('No se pudo cargar el grupo.', cause);
      showSnackbar('No se pudo cargar el grupo');
    });
  }, [load, showSnackbar]);

  const back = () => {
    if (mode === 'member') {
      setMode('info');
      setMember(null);
      return;
    }
    if (mode === 'invite') {
      setMode('info');
      setSelected([]);
      return;
    }
    onBack();
  };

  const active = group?.groupId === groupId ? group : null;
  const members = active?.members ?? [];
  const selfId = useSelfUpeerId();
  const isAdmin = active?.adminUpeerId === selfId;
  const nameOf = (upeerId: string) => contacts.find((item) => item.upeerId === upeerId)?.name ?? `${upeerId.slice(0, 10)}…`;
  const memberLabel = (upeerId: string) => (upeerId === active?.adminUpeerId ? 'Administrador' : upeerId === selfId ? 'Tú' : 'Miembro');
  const available = contacts.filter((contact) => contact.status === 'connected' && !members.includes(contact.upeerId));

  const run = async (action: () => Promise<void>, success: string) => {
    if (busy) return;
    setBusy(true);
    try {
      await action();
      await load();
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
    Alert.alert('Salir del grupo', 'Dejarás de recibir los mensajes de este grupo en este dispositivo.', [
      { text: 'Cancelar', style: 'cancel' },
      { text: 'Salir', style: 'destructive', onPress: () => void run(() => leaveGroup(groupId), 'Has salido del grupo') },
    ]);
  };

  const confirmRemove = (target: string) => {
    Alert.alert('Expulsar del grupo', `${nameOf(target)} dejará de recibir los mensajes y se rotará la clave del grupo.`, [
      { text: 'Cancelar', style: 'cancel' },
      { text: 'Expulsar', style: 'destructive', onPress: () => void run(() => removeGroupMember(groupId, target), 'Miembro expulsado') },
    ]);
  };

  const heading = mode === 'invite' ? 'Invitar al grupo' : mode === 'member' ? nameOf(member ?? '') : 'Info. del grupo';

  return (
    <SafeAreaView edges={['top', 'bottom']} style={[styles.root, { backgroundColor: theme.background }]}>
      <XStack alignItems="center" gap={4} paddingHorizontal={8} paddingVertical={8}>
        <NativeRipplePressable accessibilityLabel="Volver" accessibilityRole="button" onPress={back} style={styles.headerButton}>
          <ArrowLeft color={theme.text} size={22} />
        </NativeRipplePressable>
        <SizableText color={theme.text} flex={1} fontSize={scale(17)} fontWeight="700" numberOfLines={1}>{heading}</SizableText>
      </XStack>
      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
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
            onPress={() => void run(async () => {
              for (const target of selected) await inviteToGroup(groupId, target);
            }, selected.length === 1 ? 'Invitación enviada' : 'Invitaciones enviadas')}
          >
            {busy ? 'Invitando…' : `Invitar${selected.length > 0 ? ` (${selected.length})` : ''}`}
          </NativeRippleButton>
        </YStack> : null}

        {mode === 'member' && member ? <YStack gap={6}>
          <Text color={theme.placeholder} fontSize={scale(12)} fontWeight="600">Miembro</Text>
          <Text color={theme.placeholder} fontFamily="monospace" fontSize={scale(11)} numberOfLines={1}>{member}</Text>
          {isAdmin ? <InteractiveRow icon={Crown} label="Transferir administración" onPress={() => void run(async () => {
            await transferGroupAdmin(groupId, member);
          }, 'Administración transferida')} /> : null}
          {isAdmin ? <InteractiveRow icon={UserMinus} label={`Expulsar a ${nameOf(member)}`} onPress={() => confirmRemove(member)} tone="danger" /> : null}
          {!isAdmin ? <Text color={theme.placeholder} fontSize={scale(12)}>Solo el administrador puede gestionar a los miembros.</Text> : null}
        </YStack> : null}

        {mode === 'info' ? <>
          <YStack alignItems="center" gap={5} paddingHorizontal={20} paddingTop={4}>
            <ProfileAvatar avatar={active?.avatar} name={active?.name || 'Grupo'} size={96} />
            <SizableText color={theme.text} fontSize={scale(20)} fontWeight="700" numberOfLines={2} textAlign="center">{active?.name || 'Grupo'}</SizableText>
            <XStack alignItems="center" gap={5}>
              <Text color={theme.placeholder} fontSize={scale(12)}>{`Grupo · ${members.length} ${members.length === 1 ? 'miembro' : 'miembros'}`}</Text>
            </XStack>
            {active?.status === 'invited' ? <Text color={theme.accent} fontSize={scale(12)}>Invitación pendiente</Text> : null}
            <XStack gap={18} paddingTop={12}>
              <InteractiveRow accessibilityLabel="Invitar contactos" disabled={!isAdmin} icon={UserPlus} label="Invitar" onPress={() => { setSelected([]); setMode('invite'); }} variant="action" />
              <InteractiveRow accessibilityLabel="Renombrar grupo" disabled={!isAdmin} icon={Pencil} label="Nombre" onPress={() => { setDraft(active?.name ?? ''); setRenaming(true); }} variant="action" />
              <InteractiveRow accessibilityLabel="Salir del grupo" icon={LogOut} label="Salir" onPress={confirmLeave} variant="action" />
            </XStack>
          </YStack>

          {renaming ? <YStack gap={8}>
            <Text color={theme.placeholder} fontSize={scale(12)} fontWeight="600">Nombre del grupo</Text>
            <Input
              accessibilityLabel="Nombre del grupo"
              autoFocus
              backgroundColor={theme.backgroundElement}
              borderColor={theme.border}
              borderRadius={24}
              color={theme.text}
              onChangeText={setDraft}
              value={draft}
            />
            <XStack gap={8}>
              <NativeRippleButton backgroundColor={theme.backgroundElement} color={theme.text} flex={1} height={44} onPress={() => setRenaming(false)}>Cancelar</NativeRippleButton>
              <NativeRippleButton
                backgroundColor={theme.accent}
                color={theme.onAccent}
                disabled={busy || draft.trim().length === 0}
                flex={1}
                fontWeight="700"
                height={44}
                onPress={() => void run(() => renameGroup(groupId, draft.trim()), 'Grupo renombrado')}
              >
                {busy ? 'Guardando…' : 'Guardar'}
              </NativeRippleButton>
            </XStack>
          </YStack> : null}

          <YStack gap={6}>
            <Text color={theme.placeholder} fontSize={scale(12)} fontWeight="600">Miembros</Text>
            {members.map((upeerId) => <InteractiveRow
              accessibilityLabel={`Opciones de ${nameOf(upeerId)}`}
              description={memberLabel(upeerId)}
              icon={() => <ProfileAvatar name={nameOf(upeerId)} size={22} />}
              key={upeerId}
              label={upeerId === selfId ? 'Tú' : nameOf(upeerId)}
              onPress={() => { setMember(upeerId); setMode('member'); }}
            />)}
            {members.length === 0 ? <Text color={theme.placeholder} fontSize={scale(12)}>Sin miembros sincronizados todavía.</Text> : null}
          </YStack>

          <YStack gap={6}>
            <Text color={theme.placeholder} fontSize={scale(12)} fontWeight="600">Administración</Text>
            <InteractiveRow icon={UserPlus} label="Invitar contactos" onPress={() => { setSelected([]); setMode('invite'); }} />
            <InteractiveRow icon={Pencil} label="Renombrar grupo" onPress={() => { setDraft(active?.name ?? ''); setRenaming(true); }} />
          </YStack>

          <YStack gap={6}>
            <Text color={theme.placeholder} fontSize={scale(12)} fontWeight="600">Zona de riesgo</Text>
            <InteractiveRow icon={LogOut} label="Salir del grupo" onPress={confirmLeave} tone="danger" />
          </YStack>
        </> : null}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  headerButton: { alignItems: 'center', borderRadius: 22, height: 44, justifyContent: 'center', width: 44 },
  content: { gap: 18, paddingBottom: 32, paddingHorizontal: 20, paddingTop: 4 },
});
