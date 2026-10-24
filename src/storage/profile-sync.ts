import { File } from 'expo-file-system';

import type { IdentityProfile } from '@/storage/identity-store';
import { setProfile } from '../../modules/upeer-transport/src/UPeerTransportModule';

async function avatarPayload(uri: string | undefined): Promise<string> {
  if (!uri) return '';
  const file = new File(uri);
  if (!file.exists) return '';
  return `data:image/jpeg;base64,${await file.base64()}`;
}

export async function publishNativeProfile(profile: IdentityProfile): Promise<void> {
  const avatar = await avatarPayload(profile.avatar);
  await setProfile(profile.alias, avatar);
}

export async function publishNativeProfileSafely(profile: IdentityProfile | null): Promise<void> {
  if (!profile) return;
  try {
    await publishNativeProfile(profile);
  } catch (cause) {
    console.warn('El perfil no se pudo publicar en el servicio nativo.', cause);
  }
}
