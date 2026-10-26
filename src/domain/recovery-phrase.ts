import { isRootIdentity, type UPeerIdentity } from '@/domain/identity-record';

export type RecoveryPhrase = {
  available: boolean;
  words: string[];
  reason: string | null;
};

const TOTAL_WORDS = 12;

export function describeRecoveryPhrase(identity: UPeerIdentity): RecoveryPhrase {
  if (!isRootIdentity(identity)) {
    return { available: false, words: [], reason: 'Esta identidad se creó en otro dispositivo. La frase solo se puede ver en el dispositivo que la generó.' };
  }
  const words = identity.mnemonic.trim().split(/\s+/).filter(Boolean);
  if (words.length !== TOTAL_WORDS) {
    return { available: false, words: [], reason: 'Esta instalación entró en la cuenta sin guardar la frase de recuperación.' };
  }
  return { available: true, words, reason: null };
}
