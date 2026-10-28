import { beforeEach, describe, expect, it, vi } from 'vitest';

import { attachmentSendMessage, canSendAttachment, sendAttachment } from './attachment-send';

const native = vi.hoisted(() => ({
  calls: [] as unknown[][],
  failure: null as Error | null,
}));

vi.mock('../../modules/upeer-transport/src/UPeerTransportModule', () => ({
  sendFile: async (...args: unknown[]) => {
    if (native.failure) throw native.failure;
    native.calls.push(args);
    return 'message-1';
  },
}));

const attachment = { path: '/cache/informe.pdf', fileName: 'informe.pdf', mimeType: 'application/pdf' };

beforeEach(() => {
  native.calls = [];
  native.failure = null;
});

describe('canSendAttachment', () => {
  it('admite chats directos y grupos', () => {
    expect(canSendAttachment('a'.repeat(64))).toBe(true);
    expect(canSendAttachment('grp-123')).toBe(true);
    expect(canSendAttachment(undefined)).toBe(false);
    expect(canSendAttachment('')).toBe(false);
  });
});

describe('sendAttachment', () => {
  it('envía la ruta, el nombre y el tipo al transporte', async () => {
    const messageId = await sendAttachment({ attachment, chatUpeerId: 'grp-ignored', upeerId: 'b'.repeat(64) });
    expect(messageId).toBe('message-1');
    expect(native.calls[0]).toEqual([{
      caption: null,
      chatUpeerId: 'grp-ignored',
      fileName: 'informe.pdf',
      isVoiceNote: false,
      mimeType: 'application/pdf',
      path: '/cache/informe.pdf',
      upeerId: 'b'.repeat(64),
    }]);
  });

  it('marca las notas de voz para que el receptor las reconozca', async () => {
    const voice = { path: '/cache/nueva.m4a', fileName: 'voice_note_1.m4a', mimeType: 'audio/mp4' };
    await sendAttachment({ attachment: voice, isVoiceNote: true, upeerId: 'd'.repeat(64) });
    expect(native.calls[0][0]).toMatchObject({ isVoiceNote: true, mimeType: 'audio/mp4' });
  });

  it('envía a un grupo con su identificador de chat', async () => {
    await sendAttachment({ attachment, upeerId: 'grp-123', chatUpeerId: 'grp-123' });
    expect(native.calls[0][0]).toMatchObject({ chatUpeerId: 'grp-123', upeerId: 'grp-123' });
  });

  it('propaga el error del transporte', async () => {
    native.failure = new Error('El contacto no es conocido');
    await expect(sendAttachment({ attachment, upeerId: 'c'.repeat(64) })).rejects.toThrow('no es conocido');
  });
});

describe('attachmentSendMessage', () => {
  it('traduce los errores conocidos', () => {
    expect(attachmentSendMessage(new Error('El contacto x no es conocido'))).toBe('Solo puedes enviar archivos a un contacto de confianza');
    expect(attachmentSendMessage(new Error('El transporte no está iniciado'))).toBe('El transporte no está listo; espera unos segundos');
    expect(attachmentSendMessage(new Error('el grupo no está disponible'))).toBe('El grupo ya no está disponible en este dispositivo');
    expect(attachmentSendMessage(new Error('no tiene miembros a los que enviar'))).toBe('No hay miembros del grupo a los que enviar el archivo');
    expect(attachmentSendMessage(new Error('otra cosa'))).toBe('No se pudo enviar el archivo');
  });
});
