import { sha256 } from '@noble/hashes/sha2.js';
import { bytesToHex } from '@noble/hashes/utils.js';

export function deriveStablePeerSelection(identityKey: string, candidates: string[], count = 3): string[] {
  const selected = [...candidates].sort((left, right) => {
    const leftHash = sha256(new TextEncoder().encode(`${identityKey}:${left}`));
    const rightHash = sha256(new TextEncoder().encode(`${identityKey}:${right}`));
    return bytesToHex(leftHash).localeCompare(bytesToHex(rightHash));
  });
  return selected.slice(0, count);
}