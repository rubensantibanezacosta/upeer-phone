import { beforeEach, describe, expect, it, vi } from 'vitest';

import { attachmentRequestTargets } from './use-attachment-request';

const native = vi.hoisted(() => ({
  groups: [] as { groupId: string; members: string[]; status: string }[],
  profile: null as { upeerId: string } | null,
}));

vi.mock('../../modules/upeer-transport/src/UPeerTransportModule', () => ({
  getGroups: async () => native.groups,
  requestFile: async () => true,
}));

vi.mock('@/storage/identity-store', () => ({
  loadProfile: async () => native.profile,
}));

vi.mock('@/components/snackbar', () => ({
  useSnackbar: () => ({ showSnackbar: () => undefined }),
}));

describe('attachmentRequestTargets', () => {
  beforeEach(() => {
    native.profile = { upeerId: 'AAA' };
    native.groups = [
      { groupId: 'grp-1', members: ['aaa', 'bbb', 'ccc'], status: 'active' },
      { groupId: 'grp-2', members: ['aaa', 'bbb'], status: 'removed' },
    ];
  });

  it('pide el archivo directamente al contacto en un chat individual', async () => {
    await expect(attachmentRequestTargets('bbb')).resolves.toEqual(['bbb']);
  });

  it('pide el archivo al resto de miembros en un grupo', async () => {
    await expect(attachmentRequestTargets('grp-1')).resolves.toEqual(['bbb', 'ccc']);
  });

  it('no pide nada en un grupo eliminado', async () => {
    await expect(attachmentRequestTargets('grp-2')).resolves.toEqual([]);
  });

  it('no pide nada sin conversación', async () => {
    await expect(attachmentRequestTargets('')).resolves.toEqual([]);
  });

  it('descarta los identificadores vacíos', async () => {
    native.groups = [{ groupId: 'grp-3', members: ['aaa', '', 'ddd'], status: 'active' }];
    await expect(attachmentRequestTargets('grp-3')).resolves.toEqual(['ddd']);
  });
});
