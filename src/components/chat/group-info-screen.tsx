import { useCallback, useEffect, useState } from 'react';
import { Alert, ScrollView, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { ArrowLeft, BellOff, Eraser, Heart, Images, LogOut, MoreVertical, Phone, Search, Star, UserPlus, Users, Video } from '@tamagui/lucide-icons-2';
import { Input, SizableText, Text, XStack, YStack } from 'tamagui';

import { ChatStarredSheet } from '@/components/chat/chat-starred-sheet';
import { GroupInviteForm } from '@/components/chat/group-invite-form';
import { GroupMemberMenu } from '@/components/chat/group-member-menu';
import { NativeRippleButton } from '@/components/native-ripple-button';
import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { InteractiveRow } from '@/components/contact-info/interactive-row';
import { InfoSections, PlainGroup, SectionSeparator } from '@/components/settings-section-parts';
import { ContactTrustAvatar } from '@/components/contact-trust-avatar';
import { ProfileAvatar } from '@/components/profile-avatar';
import { useSnackbar } from '@/components/snackbar';
import { useConversationFlags } from '@/hooks/use-conversation-flags';
import { useSelfUpeerId } from '@/hooks/use-self-upeer-id';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';
import { flagMessage, flagsFor, type ConversationFlag } from '@/domain/conversation-flags';
import { getContacts, type UPeerContact } from '@/storage/contact-store';
import { setConversationFlag } from '@/storage/conversation-flags-store';
import { requestChatSearch } from '@/storage/chat-search-request';
import type { ProtocolGroup } from '../../../modules/upeer-transport/src/UPeerTransport.types';
import {
  deleteConversation,
  getConversations,
  getGroups,
  inviteToGroup,
  leaveGroup,
  removeGroupMember,
  renameGroup,
  setGroupFavorite,
  transferGroupAdmin,
} from '../../../modules/upeer-transport/src/UPeerTransportModule';

type GroupInfoScreenProps = {
  groupId: string;
  onBack: () => void;
  onChanged?: () => void;
  onOpenMessage?: (messageId: string) => void;
  onOpenContact?: (upeerId: string) => void;
};

type ScreenMode = 'info' | 'invite';

export function GroupInfoScreen({ groupId, onBack, onChanged, onOpenMessage = () => undefined, onOpenContact = () => undefined }: GroupInfoScreenProps) {
  const theme = useTheme();
  const scale = useTextScale();
  const { showSnackbar } = useSnackbar();
  const [group, setGroup] = useState<ProtocolGroup | null>(null);
  const [contacts, setContacts] = useState<UPeerContact[]>([]);
  const [avatars, setAvatars] = useState<Record<string, string | undefined>>({});
  const conversationFlags = useConversationFlags();
  const flags = flagsFor(conversationFlags, groupId);
  const [mode, setMode] = useState<ScreenMode>('info');
  const [selected, setSelected] = useState<string[]>([]);
  const [member, setMember] = useState<string | null>(null);
  const [starredOpen, setStarredOpen] = useState(false);
  const [renaming, setRenaming] = useState(false);
  const [draft, setDraft] = useState('');
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => Promise.all([getGroups(), getContacts(), getConversations()]).then(([groups, list, conversations]) => {
    setGroup(groups.find((item) => item.groupId === groupId) ?? null);
    setContacts(list);
    setAvatars(Object.fromEntries(conversations.map((conversation) => [conversation.upeerId, conversation.avatar || undefined])));
  }), [groupId]);

  const reportFailure = useCallback((cause: unknown) => {
    console.warn('No se pudo cargar el grupo.', cause);
    showSnackbar('No se pudo cargar el grupo');
  }, [showSnackbar]);

  useEffect(() => {
    load().catch(reportFailure);
  }, [load, reportFailure]);

  const back = () => {
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

  const toggleFlag = (flag: ConversationFlag) => {
    setConversationFlag(groupId, flag)
      .then(() => showSnackbar(flagMessage(flag, !flags[flag])))
      .catch((cause: unknown) => {
        console.warn('No se pudo cambiar el ajuste del grupo.', cause);
        showSnackbar('No se pudo cambiar el ajuste del grupo');
      });
  };

  const pending = (label: string) => () => showSnackbar(`${label}: pendiente en el roadmap`);

  const toggleFavorite = () => {
    const next = active?.isFavorite !== true;
    setGroupFavorite(groupId, next)
      .then(() => { load().catch(reportFailure); onChanged?.(); showSnackbar(next ? 'Añadido a Favoritos' : 'Quitado de Favoritos'); })
      .catch((cause: unknown) => {
        console.warn('No se pudo cambiar el favorito del grupo.', cause);
        showSnackbar('No se pudo cambiar el favorito del grupo');
      });
  };

  const openSearch = () => {
    requestChatSearch(groupId);
    onBack();
  };

  const confirmClearChat = () => {
    Alert.alert('Vaciar chat', 'Se borrarán todos los mensajes de esta conversación en este dispositivo. El grupo se conserva.', [
      { text: 'Cancelar', style: 'cancel' },
      {
        text: 'Vaciar',
        style: 'destructive',
        onPress: () => {
          deleteConversation(groupId)
            .then(() => { showSnackbar('Conversación vaciada'); onChanged?.(); })
            .catch((cause: unknown) => {
              console.warn('No se pudo vaciar la conversación.', cause);
              showSnackbar('No se pudo vaciar la conversación');
            });
        },
      },
    ]);
  };

  const confirmRemove = (target: string) => {
    Alert.alert('Expulsar del grupo', `${nameOf(target)} dejará de recibir los mensajes y se rotará la clave del grupo.`, [
      { text: 'Cancelar', style: 'cancel' },
      { text: 'Expulsar', style: 'destructive', onPress: () => void run(() => removeGroupMember(groupId, target), 'Miembro expulsado') },
    ]);
  };

  const heading = mode === 'invite' ? 'Invitar al grupo' : 'Info. del grupo';

  return (
    <SafeAreaView edges={['top', 'bottom']} style={[styles.root, { backgroundColor: theme.background }]}>
      <XStack alignItems="center" gap={4} paddingHorizontal={8} paddingVertical={8}>
        <NativeRipplePressable accessibilityLabel="Volver" accessibilityRole="button" onPress={back} style={styles.headerButton}>
          <ArrowLeft color={theme.text} size={22} />
        </NativeRipplePressable>
        <SizableText color={theme.text} flex={1} fontSize={scale(17)} fontWeight="700" numberOfLines={1}>{heading}</SizableText>
      </XStack>
      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
        {mode === 'invite' ? <GroupInviteForm
          available={available}
          busy={busy}
          onInvite={() => void run(async () => {
            for (const target of selected) await inviteToGroup(groupId, target);
          }, selected.length === 1 ? 'Invitación enviada' : 'Invitaciones enviadas')}
          onToggle={(contact) => setSelected((current) => (current.includes(contact.upeerId)
            ? current.filter((id) => id !== contact.upeerId)
            : [...current, contact.upeerId]))}
          selected={selected}
        /> : null}

        {mode === 'info' ? <>
          <YStack alignItems="center" gap={5} paddingTop={4}>
            {avatars[groupId] ? <ProfileAvatar avatar={avatars[groupId]} name={active?.name || 'Grupo'} size={96} /> : <View style={[styles.groupAvatar, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}><Users color={theme.placeholder} size={40} /></View>}
            <NativeRipplePressable accessibilityLabel="Renombrar grupo" accessibilityRole="button" disabled={!isAdmin} onPress={() => { setDraft(active?.name ?? ''); setRenaming(true); }}>
              <SizableText color={theme.text} fontSize={scale(20)} fontWeight="700" numberOfLines={2} textAlign="center">{active?.name || 'Grupo'}</SizableText>
            </NativeRipplePressable>
            <XStack alignItems="center" gap={5}>
              <Text color={theme.placeholder} fontSize={scale(12)}>{`Grupo · ${members.length} ${members.length === 1 ? 'miembro' : 'miembros'}`}</Text>
            </XStack>
            {active?.status === 'invited' ? <Text color={theme.accent} fontSize={scale(12)}>Invitación pendiente</Text> : null}
            <XStack gap={18} paddingTop={12}>
              <InteractiveRow accessibilityLabel="Llamar al grupo" disabled icon={Phone} label="Llamar" onPress={pending('Llamada de voz')} variant="action" />
              <InteractiveRow accessibilityLabel="Videollamar al grupo" disabled icon={Video} label="Vídeo" onPress={pending('Videollamada')} variant="action" />
              <InteractiveRow accessibilityLabel="Buscar en el chat" icon={Search} label="Buscar" onPress={openSearch} variant="action" />
              <InteractiveRow accessibilityLabel="Añadir miembros" disabled={!isAdmin} icon={UserPlus} label="Añadir" onPress={() => { setSelected([]); setMode('invite'); }} variant="action" />
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
              <NativeRippleButton backgroundColor={theme.backgroundElement} borderColor={theme.border} borderWidth={1} color={theme.text} flex={1} height={46} onPress={() => setRenaming(false)}>Cancelar</NativeRippleButton>
              <NativeRippleButton
                backgroundColor={theme.accent}
                color={theme.onAccent}
                disabled={busy || draft.trim().length === 0}
                flex={1}
                fontWeight="700"
                height={46}
                onPress={() => void run(() => renameGroup(groupId, draft.trim()), 'Grupo renombrado')}
              >
                {busy ? 'Guardando…' : 'Guardar'}
              </NativeRippleButton>
            </XStack>
          </YStack> : null}

          <InfoSections>
            <SectionSeparator />
            <Text color={theme.text} fontSize={scale(15)} fontWeight="700" paddingHorizontal={16} paddingTop={8} paddingBottom={8}>Miembros</Text>
            <PlainGroup>
              {members.length === 0
                ? <Text color={theme.placeholder} fontSize={scale(15)} paddingHorizontal={16} paddingVertical={12}>No hay miembros disponibles.</Text>
                : members.map((upeerId) => <InteractiveRow
                  accessibilityLabel={`Info. de ${nameOf(upeerId)}`}
                  icon={() => <ContactTrustAvatar avatar={avatars[upeerId]} name={nameOf(upeerId)} size={40} />}
                  key={upeerId}
                  label={upeerId === selfId ? 'Tú' : nameOf(upeerId)}
                  onPress={() => (upeerId === selfId ? undefined : onOpenContact(upeerId))}
                  trailing={upeerId === selfId ? undefined : <NativeRipplePressable accessibilityLabel={`Acciones de ${nameOf(upeerId)}`} accessibilityRole="button" onPress={() => setMember(upeerId)} style={styles.memberMenu}><MoreVertical color={theme.placeholder} size={19} /></NativeRipplePressable>}
                />)}
            </PlainGroup>

            <SectionSeparator />
            <InteractiveRow chevron icon={Images} label="Multimedia" onPress={pending('Multimedia')} />
            <SectionSeparator />

            <PlainGroup>
              <InteractiveRow icon={BellOff} label={flags.muted ? 'Reactivar notificaciones' : 'Silenciar notificaciones'} onPress={() => toggleFlag('muted')} />
              <InteractiveRow icon={() => <Heart color={theme.text} fill={active?.isFavorite ? 'currentColor' : 'none'} size={22} />} label={active?.isFavorite ? 'Quitar de Favoritos' : 'Añadir a Favoritos'} onPress={toggleFavorite} />
              <InteractiveRow chevron icon={Star} label="Mensajes destacados" onPress={() => setStarredOpen(true)} />
            </PlainGroup>

            <SectionSeparator />
            <PlainGroup>
              <InteractiveRow icon={Eraser} label="Vaciar chat" onPress={confirmClearChat} />
              <InteractiveRow icon={LogOut} label="Salir del grupo" onPress={confirmLeave} tone="danger" />
            </PlainGroup>
          </InfoSections>
        </> : null}
      </ScrollView>
      <GroupMemberMenu
        avatar={member ? avatars[member] : undefined}
        canManage={isAdmin}
        memberId={member}
        name={nameOf(member ?? '')}
        onClose={() => setMember(null)}
        onRemove={() => { const target = member; setMember(null); if (target) confirmRemove(target); }}
        onTransferAdmin={() => { const target = member; setMember(null); if (target) void run(async () => { await transferGroupAdmin(groupId, target); }, 'Administración transferida'); }}
      />
      <ChatStarredSheet
        messages={[]}
        onClose={() => setStarredOpen(false)}
        onOpenMessage={(messageId) => { setStarredOpen(false); onOpenMessage(messageId); }}
        upeerId={groupId}
        visible={starredOpen}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  headerButton: { alignItems: 'center', borderRadius: 22, height: 44, justifyContent: 'center', width: 44 },
  content: { gap: 18, paddingBottom: 32, paddingHorizontal: 20, paddingTop: 4 },
  memberMenu: { alignItems: 'center', borderRadius: 20, height: 40, justifyContent: 'center', width: 40 },
  groupAvatar: { alignItems: 'center', borderRadius: 48, borderWidth: StyleSheet.hairlineWidth, height: 96, justifyContent: 'center', width: 96 },
});
