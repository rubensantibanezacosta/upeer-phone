import type { IdentityProfile } from '@/storage/identity-store';
import { loadProfile, saveProfile } from '@/storage/identity-store';
import { decodeAvatarData } from '@/storage/avatar-data';
import { withProfilePublishSuppressed } from '@/storage/settings-sync-client';

const ALIAS_MAX = 64;

type AvatarDecision = 'keep' | 'clear' | { data: string };

function resolveAvatar(value: unknown): AvatarDecision {
  if (typeof value !== 'string') return 'keep';
  const raw = value.trim();
  if (!raw) return 'clear';
  try {
    decodeAvatarData(raw);
    return { data: raw };
  } catch (cause: unknown) {
    console.warn('La foto de perfil sincronizada no es válida y se descarta.', cause);
    return 'keep';
  }
}

export async function applyRemoteProfile(values: Record<string, unknown>): Promise<void> {
  const current = await loadProfile();
  if (!current) return;
  const alias = typeof values.alias === 'string' ? values.alias.trim().slice(0, ALIAS_MAX) : '';
  const avatar = resolveAvatar(values.avatar);
  const aliasChanged = Boolean(alias) && alias !== current.alias;
  const avatarChanged = avatar !== 'keep';
  if (!aliasChanged && !avatarChanged) return;
  const patch: IdentityProfile = {
    upeerId: current.upeerId,
    publicKey: current.publicKey,
    alias: aliasChanged ? alias : current.alias,
  };
  if (avatar !== 'keep') patch.avatar = avatar === 'clear' ? '' : avatar.data;
  await withProfilePublishSuppressed(() => saveProfile(patch));
}
