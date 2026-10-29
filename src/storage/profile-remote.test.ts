import { beforeEach, describe, expect, it, vi } from 'vitest';

const fs = vi.hoisted(() => ({
  files: new Map<string, Uint8Array>(),
  secret: new Map<string, string>(),
  published: [] as { block: string }[],
  nativeProfile: { alias: '', avatar: '' },
}));

vi.mock('expo-secure-store', () => ({
  getItemAsync: async (key: string) => fs.secret.get(key) ?? null,
  setItemAsync: async (key: string, value: string) => { fs.secret.set(key, value); },
  deleteItemAsync: async (key: string) => { fs.secret.delete(key); },
}));

vi.mock('expo-sqlite/kv-store', () => ({
  default: {
    getItemAsync: async (key: string) => fs.secret.get(key) ?? null,
    setItemAsync: async (key: string, value: string) => { fs.secret.set(key, value); },
  },
}));

vi.mock('expo-file-system', () => {
  const uriOf = (parts: unknown[]): string => parts
    .map((part) => (typeof part === 'string' ? part : (part as { uri: string }).uri))
    .join('/');

  class Directory {
    readonly uri: string;
    constructor(...parts: unknown[]) { this.uri = uriOf(parts); }
    get exists() { return true; }
    async create() { return undefined; }
  }

  class File {
    readonly uri: string;
    constructor(...parts: unknown[]) { this.uri = uriOf(parts); }
    get exists() { return fs.files.has(this.uri); }
    async write(bytes: Uint8Array) { fs.files.set(this.uri, bytes); }
    async delete() { fs.files.delete(this.uri); }
    async base64() {
      const bytes = fs.files.get(this.uri);
      if (!bytes) throw new Error('Archivo inexistente');
      return btoa(String.fromCharCode(...bytes));
    }
  }

  return { Directory, File, Paths: { document: { uri: 'file:///document' } } };
});

vi.mock('@/storage/contact-store', () => ({
  deleteContactDatabase: vi.fn(async () => undefined),
}));

vi.mock('../../modules/upeer-transport/src/UPeerTransportModule', () => ({
  generateNodeSeed: () => 'seed',
  setProfile: async (alias: string, avatar: string) => { fs.nativeProfile = { alias, avatar }; },
  publishSettingsPatch: async (payload: { block: string }) => { fs.published.push(payload); },
  readSyncSettingsEvents: async () => ({ cursor: 0, events: [] }),
}));

const JPEG = 'data:image/jpeg;base64,/9j/2Q==';

describe('perfil de la cuenta sincronizado', () => {
  beforeEach(() => {
    fs.files.clear();
    fs.secret.clear();
    fs.published.length = 0;
    fs.nativeProfile = { alias: '', avatar: '' };
  });

  it('adopta el alias y la foto que llegan de otro dispositivo sin reenviarlos', async () => {
    const { saveProfile } = await import('@/storage/identity-store');
    const { applyRemoteProfile } = await import('@/storage/profile-remote');
    await saveProfile({ upeerId: 'u1', publicKey: 'p1', alias: 'Local' });
    fs.published.length = 0;

    await applyRemoteProfile({ alias: 'Remoto', avatar: JPEG });

    const { loadProfile } = await import('@/storage/identity-store');
    const profile = await loadProfile();
    expect(profile?.alias).toBe('Remoto');
    expect(fs.files.size).toBe(1);
    expect([...fs.files.values()][0]).toEqual(new Uint8Array([0xff, 0xd8, 0xff, 0xd9]));
    expect(fs.published).toEqual([]);
  });

  it('un alias nuevo conserva la foto que ya había', async () => {
    const { loadProfile, saveProfile } = await import('@/storage/identity-store');
    const { applyRemoteProfile } = await import('@/storage/profile-remote');
    await saveProfile({ upeerId: 'u1', publicKey: 'p1', alias: 'Local', avatar: JPEG });
    fs.published.length = 0;

    await applyRemoteProfile({ alias: 'Otro' });

    const profile = await loadProfile();
    expect(profile?.alias).toBe('Otro');
    expect(profile?.avatar).toMatch(/^file:\/\/\/document\/profile\/avatar-\d+\.jpg$/);
    expect(fs.published).toEqual([]);
  });

  it('descarta una foto inválida pero aplica el alias', async () => {
    const { applyRemoteProfile } = await import('@/storage/profile-remote');
    const { loadProfile, saveProfile } = await import('@/storage/identity-store');
    await saveProfile({ upeerId: 'u1', publicKey: 'p1', alias: 'Local', avatar: JPEG });
    fs.files.clear();
    fs.published.length = 0;

    await applyRemoteProfile({ alias: 'Otro', avatar: 'data:image/png;base64,AAAA' });

    expect((await loadProfile())?.alias).toBe('Otro');
    expect(fs.files.size).toBe(0);
    expect(fs.published).toEqual([]);
  });

  it('un avatar vacío borra la foto guardada', async () => {
    const { applyRemoteProfile } = await import('@/storage/profile-remote');
    const { loadProfile, saveProfile } = await import('@/storage/identity-store');
    await saveProfile({ upeerId: 'u1', publicKey: 'p1', alias: 'Local', avatar: JPEG });
    fs.published.length = 0;

    await applyRemoteProfile({ avatar: '' });

    expect((await loadProfile())?.avatar).toBeUndefined();
    expect(fs.files.size).toBe(0);
    expect(fs.published).toEqual([]);
  });

  it('un perfil idéntico al guardado no toca nada', async () => {
    const { applyRemoteProfile } = await import('@/storage/profile-remote');
    const { saveProfile } = await import('@/storage/identity-store');
    await saveProfile({ upeerId: 'u1', publicKey: 'p1', alias: 'Igual' });
    fs.published.length = 0;

    await applyRemoteProfile({ alias: 'Igual', avatar: '' });

    expect(fs.published).toEqual([]);
  });

  it('crea el perfil local si el dispositivo aún no tenía ninguno', async () => {
    const { saveIdentity } = await import('@/storage/identity-store');
    const { applyRemoteProfile } = await import('@/storage/profile-remote');
    const { loadProfile } = await import('@/storage/identity-store');
    await saveIdentity({ mode: 'device', upeerId: 'u1', publicKey: 'p1' });
    fs.published.length = 0;

    await applyRemoteProfile({ alias: 'Remoto', avatar: JPEG });

    const profile = await loadProfile();
    expect(profile?.upeerId).toBe('u1');
    expect(profile?.publicKey).toBe('p1');
    expect(profile?.alias).toBe('Remoto');
    expect(fs.files.size).toBe(1);
    expect(fs.published).toEqual([]);
  });

  it('sin identidad ni perfil no inventa nada', async () => {
    const { applyRemoteProfile } = await import('@/storage/profile-remote');

    await applyRemoteProfile({ alias: 'Remoto', avatar: JPEG });

    expect(fs.files.size).toBe(0);
    expect(fs.secret.size).toBe(0);
    expect(fs.published).toEqual([]);
  });

  it('un cambio hecho por el usuario sí se publica', async () => {
    const { saveProfile } = await import('@/storage/identity-store');

    await saveProfile({ upeerId: 'u1', publicKey: 'p1', alias: 'Mío', avatar: JPEG });

    expect(fs.published).toEqual([{ block: 'profile', alias: 'Mío', avatar: JPEG }]);
    expect(fs.nativeProfile).toEqual({ alias: 'Mío', avatar: JPEG });
  });
});
