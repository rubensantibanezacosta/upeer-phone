import { getContacts } from '@/storage/contact-store';
import { parseReputationScores, type ReputationScores } from '@/domain/reputation';
import { getReputationScores } from '../../modules/upeer-transport/src/UPeerTransportModule';

const REFRESH_MS = 60_000;
const DIRECT_STATUSES = new Set(['connected', 'offline']);

let current: ReputationScores = {};
let timer: ReturnType<typeof setInterval> | null = null;
let running = false;
const listeners = new Set<() => void>();

function emit() {
  for (const listener of listeners) listener();
}

export function subscribeReputationScores(listener: () => void): () => void {
  listeners.add(listener);
  return () => { listeners.delete(listener); };
}

export function getReputationScoresSnapshot(): ReputationScores {
  return current;
}

export async function refreshReputationScores(): Promise<void> {
  try {
    const contacts = await getContacts();
    if (contacts.length === 0) {
      if (Object.keys(current).length > 0) {
        current = {};
        emit();
      }
      return;
    }
    const subjects = contacts.map((contact) => contact.upeerId);
    const direct = contacts.filter((contact) => DIRECT_STATUSES.has(contact.status)).map((contact) => contact.upeerId);
    const raw = await getReputationScores(JSON.stringify(subjects), JSON.stringify(direct));
    const next = parseReputationScores(raw);
    current = next;
    emit();
  } catch (cause: unknown) {
    console.warn('No se pudo leer la reputación de los contactos.', cause);
  }
}

export function startReputationPolling(): void {
  if (running) return;
  running = true;
  void refreshReputationScores();
  timer = setInterval(() => { void refreshReputationScores(); }, REFRESH_MS);
}

export function stopReputationPolling(): void {
  if (timer) clearInterval(timer);
  timer = null;
  running = false;
}
