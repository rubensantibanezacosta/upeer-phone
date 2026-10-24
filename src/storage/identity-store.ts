import * as SecureStore from 'expo-secure-store';
import { Directory, File, Paths } from 'expo-file-system';

import { generateNodeSeed } from '../../modules/upeer-transport/src/UPeerTransportModule';
import type { UPeerIdentity } from '@/domain/identity';
import { deleteContactDatabase } from '@/storage/contact-store';
import { decodeAvatarData } from '@/storage/avatar-data';
import { publishNativeProfileSafely } from '@/storage/profile-sync';

const IDENTITY_KEY = 'upeer.identity.v1';
const PROFILE_KEY = 'upeer.profile.v1';
const YGGDRASIL_SEED_KEY = 'upeer.yggdrasil.seed.v1';

export type IdentityProfile = {
  upeerId: string;
  publicKey: string;
  alias: string;
  avatar?: string;
};

const AVATAR_DIRECTORY = new Directory(Paths.document, 'profile');

export async function loadIdentity(): Promise<UPeerIdentity | null> {
  const stored = await SecureStore.getItemAsync(IDENTITY_KEY);
  return stored ? JSON.parse(stored) as UPeerIdentity : null;
}

export async function saveIdentity(identity: UPeerIdentity): Promise<void> {
  await SecureStore.setItemAsync(IDENTITY_KEY, JSON.stringify(identity));
}

export async function loadProfile(): Promise<IdentityProfile | null> {
  const stored = await SecureStore.getItemAsync(PROFILE_KEY);
  if (!stored) return null;
  const profile = JSON.parse(stored) as Omit<IdentityProfile, 'avatar'>;
  const avatarUri = await SecureStore.getItemAsync(`${PROFILE_KEY}.avatar`);
  const avatarFile = avatarUri ? new File(avatarUri) : null;
  return { ...profile, avatar: avatarFile?.exists ? avatarUri ?? undefined : undefined };
}

export async function saveProfile(profile: IdentityProfile): Promise<void> {
  await persistProfile(profile);
  await publishNativeProfileSafely(await loadProfile());
}

async function persistProfile(profile: IdentityProfile): Promise<void> {
  const { avatar, ...metadata } = profile;
  const avatarKey = `${PROFILE_KEY}.avatar`;
  const previousAvatarUri = await SecureStore.getItemAsync(avatarKey);
  if (avatar === undefined) {
    await SecureStore.setItemAsync(PROFILE_KEY, JSON.stringify(metadata));
    return;
  }
  if (avatar && avatar === previousAvatarUri) {
    await SecureStore.setItemAsync(PROFILE_KEY, JSON.stringify(metadata));
    return;
  }
  if (avatar) {
    if (!AVATAR_DIRECTORY.exists) await AVATAR_DIRECTORY.create({ intermediates: true, idempotent: true });
    const bytes = decodeAvatarData(avatar);
    let nextAvatar = new File(AVATAR_DIRECTORY, `avatar-${Date.now()}.jpg`);
    let suffix = 1;
    while (nextAvatar.exists) {
      nextAvatar = new File(AVATAR_DIRECTORY, `avatar-${Date.now()}-${suffix}.jpg`);
      suffix += 1;
    }
    try {
      await nextAvatar.write(bytes);
      await SecureStore.setItemAsync(PROFILE_KEY, JSON.stringify(metadata));
      await SecureStore.setItemAsync(avatarKey, nextAvatar.uri);
    } catch (cause) {
      if (nextAvatar.exists) await nextAvatar.delete();
      throw cause;
    }
    if (previousAvatarUri && previousAvatarUri !== nextAvatar.uri) {
      const previousAvatar = new File(previousAvatarUri);
      if (previousAvatar.exists) await previousAvatar.delete();
    }
    return;
  }
  await SecureStore.setItemAsync(PROFILE_KEY, JSON.stringify(metadata));
  await SecureStore.deleteItemAsync(avatarKey);
  if (previousAvatarUri) {
    const previousAvatar = new File(previousAvatarUri);
    if (previousAvatar.exists) await previousAvatar.delete();
  }
}

export async function removeProfileAvatar(): Promise<void> {
  await deleteStoredAvatar();
  const profile = await loadProfile();
  if (profile) await publishNativeProfileSafely({ ...profile, avatar: undefined });
}

async function deleteStoredAvatar(): Promise<void> {
  const avatarKey = `${PROFILE_KEY}.avatar`;
  const previousAvatarUri = await SecureStore.getItemAsync(avatarKey);
  await SecureStore.deleteItemAsync(avatarKey);
  if (previousAvatarUri) {
    const previousAvatar = new File(previousAvatarUri);
    if (previousAvatar.exists) await previousAvatar.delete();
  }
}

export async function loadOrCreateYggdrasilSeed(): Promise<Uint8Array> {
  const stored = await SecureStore.getItemAsync(YGGDRASIL_SEED_KEY);
  if (stored) {
    if (!/^[0-9a-f]{64}$/i.test(stored)) throw new Error('La clave de conexión guardada no es válida.');
    return Uint8Array.from(stored.match(/.{2}/g) ?? [], (byte) => Number.parseInt(byte, 16));
  }
  const seed = await generateNodeSeed();
  await SecureStore.setItemAsync(YGGDRASIL_SEED_KEY, Array.from(seed, (byte) => byte.toString(16).padStart(2, '0')).join(''));
  return seed;
}

export async function replaceYggdrasilSeed(): Promise<Uint8Array> {
  const seed = await generateNodeSeed();
  await SecureStore.setItemAsync(YGGDRASIL_SEED_KEY, Array.from(seed, (byte) => byte.toString(16).padStart(2, '0')).join(''));
  return seed;
}

export async function clearIdentity(): Promise<void> {
  await SecureStore.deleteItemAsync(IDENTITY_KEY);
  await SecureStore.deleteItemAsync(PROFILE_KEY);
  await removeProfileAvatar();
  await SecureStore.deleteItemAsync(YGGDRASIL_SEED_KEY);
  await deleteContactDatabase();
}
