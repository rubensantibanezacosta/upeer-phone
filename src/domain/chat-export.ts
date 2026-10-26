import type { ProtocolMessage } from '../../modules/upeer-transport/src/UPeerTransport.types';

export type ChatExportInput = {
  chatName: string;
  meLabel: string;
  messages: ProtocolMessage[];
  exportedAt: number;
};

const pad = (value: number): string => String(value).padStart(2, '0');

export function formatExportStamp(timestamp: number): string {
  const date = new Date(timestamp);
  return `${pad(date.getDate())}/${pad(date.getMonth() + 1)}/${date.getFullYear()} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

export function buildChatTranscript({ chatName, meLabel, messages, exportedAt }: ChatExportInput): string {
  const header = [`Conversación: ${chatName}`, `Exportada: ${formatExportStamp(exportedAt)}`, `Mensajes: ${messages.length}`, ''];
  const lines = messages.map((message) => {
    const author = message.isMine ? meLabel : chatName;
    const body = message.deleted ? 'Mensaje eliminado' : message.content || '[adjunto]';
    const edited = message.isEdited ? ' (editado)' : '';
    return `[${formatExportStamp(message.timestamp)}] ${author}: ${body}${edited}`;
  });
  return [...header, ...lines, ''].join('\n');
}

export function transcriptFileName(chatName: string): string {
  const safe = chatName.replace(/[\\/:*?"<>|]+/g, '-').replace(/\s+/g, ' ').trim();
  return `${safe || 'conversacion'}.txt`;
}
