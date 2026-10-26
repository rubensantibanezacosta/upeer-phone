import { File, Paths } from 'expo-file-system';
import * as Sharing from 'expo-sharing';

import { buildChatTranscript, transcriptFileName, type ChatExportInput } from '@/domain/chat-export';

export type ChatExportResult = 'shared' | 'unsupported';

export async function shareChatTranscript(input: ChatExportInput): Promise<ChatExportResult> {
  if (!(await Sharing.isAvailableAsync())) return 'unsupported';
  const file = new File(Paths.cache, transcriptFileName(input.chatName));
  if (file.exists) file.delete();
  await file.write(buildChatTranscript(input));
  await Sharing.shareAsync(file.uri, { mimeType: 'text/plain', dialogTitle: file.name });
  return 'shared';
}
