import { useState } from 'react';
import { Alert, StyleSheet, View } from 'react-native';
import { ListPlus, Pencil, Trash2, UsersRound } from '@tamagui/lucide-icons-2';
import { Input, SizableText, Text, XStack, YStack } from 'tamagui';

import { BottomSheet } from '@/components/bottom-sheet';
import { NativeRippleButton } from '@/components/native-ripple-button';
import { NativeRipplePressable } from '@/components/native-ripple-pressable';
import { describeChatList, LIST_NAME_MAX_LENGTH, type ChatList } from '@/domain/chat-lists';
import { useChatLists } from '@/hooks/use-chat-lists';
import { useTextScale } from '@/hooks/use-text-scale';
import { useTheme } from '@/hooks/use-theme';
import { createList, deleteList, renameList } from '@/storage/chat-lists-store';

type ChatListsSheetProps = {
  onClose: () => void;
  onEditChats: (list: ChatList) => void;
  visible: boolean;
};

export function ChatListsSheet({ onClose, onEditChats, visible }: ChatListsSheetProps) {
  const theme = useTheme();
  const scale = useTextScale();
  const lists = useChatLists();
  const [name, setName] = useState('');
  const [renaming, setRenaming] = useState<{ id: string; name: string } | null>(null);

  const reset = () => {
    setName('');
    setRenaming(null);
  };

  const close = () => {
    reset();
    onClose();
  };

  const submitCreate = async () => {
    if (name.trim().length === 0) return;
    await createList(name);
    setName('');
  };

  const submitRename = async () => {
    if (!renaming) return;
    await renameList(renaming.id, renaming.name);
    setRenaming(null);
  };

  const confirmDelete = (list: ChatList) => {
    Alert.alert('Eliminar lista', `«${list.name}» desaparecerá de tu dispositivo. Los chats no se borran.`, [
      { text: 'Cancelar', style: 'cancel' },
      { text: 'Eliminar', style: 'destructive', onPress: () => { void deleteList(list.id); } },
    ]);
  };

  return (
    <BottomSheet icon={<ListPlus color={theme.accent} size={20} />} onClose={close} title="Listas" visible={visible}>
      <YStack gap={6}>
        <Text color={theme.placeholder} fontSize={scale(11)}>
          Una lista agrupa los chats que tú elijas, como «Trabajo» o «Familia». Solo cambia cómo los ves.
        </Text>
        <XStack alignItems="center" gap={8}>
          <Input
            accessibilityLabel="Nombre de la lista"
            autoCapitalize="sentences"
            backgroundColor={theme.backgroundSelected}
            borderColor={theme.border}
            borderRadius={24}
            color={theme.text}
            flex={1}
            maxLength={LIST_NAME_MAX_LENGTH}
            onChangeText={setName}
            onSubmitEditing={() => { void submitCreate(); }}
            placeholder="Nueva lista"
            placeholderTextColor={theme.placeholder}
            value={name}
          />
          <NativeRippleButton
            accessibilityLabel="Crear lista"
            backgroundColor={theme.accent}
            color={theme.onAccent}
            disabled={name.trim().length === 0}
            fontWeight="700"
            height={42}
            onPress={() => { void submitCreate(); }}
          >
            Crear
          </NativeRippleButton>
        </XStack>
      </YStack>
      {lists.length === 0 ? (
        <Text color={theme.placeholder} fontSize={scale(12)} paddingVertical={12} textAlign="center">
          Todavía no tienes listas
        </Text>
      ) : lists.map((list) => (
        <View key={list.id} style={[styles.row, { borderColor: theme.border }]}>
          {renaming?.id === list.id ? (
            <XStack alignItems="center" flex={1} gap={8}>
              <Input
                accessibilityLabel="Nuevo nombre de la lista"
                autoFocus
                backgroundColor={theme.backgroundSelected}
                borderColor={theme.border}
                borderRadius={24}
                color={theme.text}
                flex={1}
                maxLength={LIST_NAME_MAX_LENGTH}
                onChangeText={(value) => setRenaming({ id: list.id, name: value })}
                onSubmitEditing={() => { void submitRename(); }}
                value={renaming.name}
              />
              <NativeRipplePressable accessibilityLabel="Guardar nombre" onPress={() => { void submitRename(); }} style={styles.iconButton}>
                <Text color={theme.accent} fontSize={scale(13)} fontWeight="700">Guardar</Text>
              </NativeRipplePressable>
            </XStack>
          ) : (
            <>
              <UsersRound color={theme.placeholder} size={18} />
              <YStack flex={1}>
                <SizableText color={theme.text} fontSize={scale(14)} fontWeight="600" numberOfLines={1}>{list.name}</SizableText>
                <Text color={theme.placeholder} fontSize={scale(11)}>{describeChatList(list)}</Text>
              </YStack>
              <NativeRipplePressable accessibilityLabel={`Elegir chats de ${list.name}`} onPress={() => onEditChats(list)} style={styles.iconButton}>
                <ListPlus color={theme.text} size={18} />
              </NativeRipplePressable>
              <NativeRipplePressable accessibilityLabel={`Renombrar ${list.name}`} onPress={() => setRenaming({ id: list.id, name: list.name })} style={styles.iconButton}>
                <Pencil color={theme.text} size={17} />
              </NativeRipplePressable>
              <NativeRipplePressable accessibilityLabel={`Eliminar ${list.name}`} onPress={() => confirmDelete(list)} style={styles.iconButton}>
                <Trash2 color={theme.danger} size={17} />
              </NativeRipplePressable>
            </>
          )}
        </View>
      ))}
    </BottomSheet>
  );
}

const styles = StyleSheet.create({
  iconButton: { alignItems: 'center', borderRadius: 18, height: 36, justifyContent: 'center', minWidth: 36, paddingHorizontal: 6 },
  row: { alignItems: 'center', borderTopWidth: StyleSheet.hairlineWidth, flexDirection: 'row', gap: 10, paddingVertical: 10 },
});
