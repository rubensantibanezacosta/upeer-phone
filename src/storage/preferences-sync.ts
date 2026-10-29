import type { NativePreferencesSnapshot } from '../../modules/upeer-transport/src/UPeerTransport.types';
import { getNativePreferences } from '../../modules/upeer-transport/src/UPeerTransportModule';
import type { Preferences } from '@/domain/preferences';
import { getPreferences, loadPreferences, updatePreferences } from './preferences-store';

type SyncedFields = Pick<Preferences, 'readReceipts' | 'shareActivity' | 'messageNotifications' | 'requestNotifications' | 'notificationSound'>;

function snapshotValues(snapshot: NativePreferencesSnapshot): SyncedFields {
  return {
    readReceipts: snapshot.readReceipts,
    shareActivity: snapshot.shareActivity,
    messageNotifications: snapshot.notifications.messages,
    requestNotifications: snapshot.notifications.requests,
    notificationSound: snapshot.notifications.sound,
  };
}

function changedFields(current: Preferences, next: SyncedFields): Partial<Preferences> {
  const patch: Partial<Preferences> = {};
  for (const field of Object.keys(next) as (keyof SyncedFields)[]) {
    if (current[field] !== next[field]) patch[field] = next[field];
  }
  return patch;
}

export async function adoptNativePreferences(): Promise<boolean> {
  await loadPreferences();
  const snapshot = await getNativePreferences();
  const patch = changedFields(getPreferences(), snapshotValues(snapshot));
  if (Object.keys(patch).length === 0) return false;
  await updatePreferences(patch);
  return true;
}
