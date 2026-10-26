import { describe, expect, it } from 'vitest';

import { buildChatTranscript, formatExportStamp, transcriptFileName } from './chat-export';
import type { ProtocolMessage } from '../../modules/upeer-transport/src/UPeerTransport.types';

function message(overrides: Partial<ProtocolMessage>): ProtocolMessage {
  return {
    content: 'hola',
    id: 'm1',
    isMine: false,
    status: 'sent',
    timestamp: new Date(2026, 9, 26, 9, 5).getTime(),
    upeerId: 'peer',
    ...overrides,
  };
}

describe('formatExportStamp', () => {
  it('escribe la fecha en formato corto con ceros a la izquierda', () => {
    expect(formatExportStamp(new Date(2026, 0, 2, 7, 4).getTime())).toBe('02/01/2026 07:04');
  });
});

describe('buildChatTranscript', () => {
  it('incluye cabecera y una línea por mensaje', () => {
    const text = buildChatTranscript({
      chatName: 'Ana',
      exportedAt: new Date(2026, 9, 26, 10, 0).getTime(),
      meLabel: 'Tú',
      messages: [message({}), message({ content: 'qué tal', id: 'm2', isMine: true })],
    });
    expect(text.split('\n')).toEqual([
      'Conversación: Ana',
      'Exportada: 26/10/2026 10:00',
      'Mensajes: 2',
      '',
      '[26/10/2026 09:05] Ana: hola',
      '[26/10/2026 09:05] Tú: qué tal',
      '',
    ]);
  });

  it('marca editados, eliminados y adjuntos', () => {
    const text = buildChatTranscript({
      chatName: 'Ana',
      exportedAt: 0,
      meLabel: 'Tú',
      messages: [message({ content: 'nuevo', isEdited: true }), message({ content: '', deleted: true, id: 'm2' }), message({ content: '', id: 'm3' })],
    });
    const lines = text.split('\n').filter((line) => line.startsWith('['));
    expect(lines).toEqual(['[26/10/2026 09:05] Ana: nuevo (editado)', '[26/10/2026 09:05] Ana: Mensaje eliminado', '[26/10/2026 09:05] Ana: [adjunto]']);
  });
});

describe('transcriptFileName', () => {
  it('sanea los caracteres que no valen en un nombre de archivo', () => {
    expect(transcriptFileName('Ana / Grupo: 3*')).toBe('Ana - Grupo- 3-.txt');
  });

  it('usa un nombre por defecto cuando no queda nada', () => {
    expect(transcriptFileName('   ')).toBe('conversacion.txt');
  });
});
