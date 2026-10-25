export type RootIdentity = {
  mode: 'root';
  mnemonic: string;
  upeerId: string;
  publicKey: string;
  secretKey: string;
};

export type DeviceIdentity = {
  mode: 'device';
  upeerId: string;
  publicKey: string;
};

export type UPeerIdentity = RootIdentity | DeviceIdentity;

export function isRootIdentity(identity: UPeerIdentity): identity is RootIdentity {
  return identity.mode === 'root';
}

export function deviceIdentity(upeerId: string, publicKey: string): DeviceIdentity {
  return { mode: 'device', upeerId, publicKey };
}

export function normalizeStoredIdentity(value: unknown): UPeerIdentity | null {
  if (value === null || typeof value !== 'object') return null;
  const record = value as Record<string, unknown>;
  const upeerId = typeof record.upeerId === 'string' ? record.upeerId : '';
  const publicKey = typeof record.publicKey === 'string' ? record.publicKey : '';
  if (!upeerId || !publicKey) return null;
  if (record.mode === 'device') return deviceIdentity(upeerId, publicKey);
  const secretKey = typeof record.secretKey === 'string' ? record.secretKey : '';
  if (record.mode !== 'root' && !secretKey) return null;
  const mnemonic = typeof record.mnemonic === 'string' ? record.mnemonic : '';
  return { mode: 'root', mnemonic, upeerId, publicKey, secretKey };
}
